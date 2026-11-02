package com.extractor.mi_extractor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Motor único de descarga paralela (ADR-001). Contiene la ÚNICA copia del bucle que antes
 * estaba duplicado en {@code DescargaCli.ejecutarDescargaParalela} (CLI) y
 * {@code descargarWebMultiHilo} (Web). CLI y Web quedan como adaptadores delgados que lo
 * invocan con su propio callback de progreso.
 *
 * <p>El navegador (y el extractor/escritor) se obtienen de {@link Supplier}s, por defecto
 * {@code Scraper::new}, etc. Eso permite inyectar un {@code Scraper} mockeado en test y validar
 * la clasificación VIP/fallo/éxito sin abrir Chrome.
 */
public class MotorDescargaParalela {

    private static final Logger log = LoggerFactory.getLogger(MotorDescargaParalela.class);

    /** Cada cuántos capítulos se recicla el navegador para liberar RAM. */
    private static final int REFRESCO_CADA = 100;
    private static final int MAX_INTENTOS = 3;

    private final Supplier<Scraper> fabricaScraper;
    private final Supplier<ExtractorContenido> fabricaExtractor;
    private final Supplier<EscritorArchivo> fabricaEscritor;

    public MotorDescargaParalela() {
        this(Scraper::new, ExtractorContenido::new, EscritorArchivo::new);
    }

    public MotorDescargaParalela(Supplier<Scraper> fabricaScraper,
            Supplier<ExtractorContenido> fabricaExtractor, Supplier<EscritorArchivo> fabricaEscritor) {
        this.fabricaScraper = fabricaScraper;
        this.fabricaExtractor = fabricaExtractor;
        this.fabricaEscritor = fabricaEscritor;
    }

    /** Resultado agregado del lote. Los sets vienen ordenados (números de capítulo exactos). */
    public static final class ResultadoLote {
        public final int exitos;
        public final int total;
        public final Set<Double> vip;
        public final Set<Double> faltantes;

        ResultadoLote(int exitos, int total, Set<Double> vip, Set<Double> faltantes) {
            this.exitos = exitos;
            this.total = total;
            this.vip = vip;
            this.faltantes = faltantes;
        }
    }

    /** Callback de progreso; cada adaptador (CLI/Web) formatea a su manera. */
    public interface ProgresoDescarga {
        /** Antes de intentar el capítulo (se usa para anunciar "bajando #N"). */
        void capituloIniciado(int hiloIdx, double numCap,
                int hechosEnHilo, int totalEnHilo, int hechosGlobal, int totalGlobal);

        /** Tras resolver el capítulo (éxito/VIP/fallo); sirve para avanzar barras. */
        void capituloTerminado(int hiloIdx, double numCap,
                int hechosEnHilo, int totalEnHilo, int hechosGlobal, int totalGlobal);

        /** Al terminar el lote de un hilo. */
        void hiloTerminado(int hiloIdx);
    }

    /**
     * Descarga en paralelo los capítulos dados. Trocea en {@code numHilos} lotes; cada hilo usa
     * su propio {@code Scraper} (reciclado tras excepción y cada {@value #REFRESCO_CADA} caps).
     * Reintenta fallos transitorios; un VIP se detecta al instante y no gasta reintentos.
     *
     * @param capitulos mapa número→URL; su orden de iteración define el troceo (pasa un TreeMap
     *                  si quieres orden ascendente determinista).
     */
    public ResultadoLote ejecutar(Map<Double, String> capitulos, String rutaBase,
            int numHilos, ProgresoDescarga cb) {

        List<Map.Entry<Double, String>> lista = new ArrayList<>(capitulos.entrySet());
        int total = lista.size();
        AtomicInteger exitos = new AtomicInteger(0);
        AtomicInteger globales = new AtomicInteger(0);
        Set<Double> vip = new ConcurrentSkipListSet<>();
        Set<Double> faltantes = new ConcurrentSkipListSet<>();

        if (total == 0) {
            return new ResultadoLote(0, 0, vip, faltantes);
        }
        int hilos = Math.max(1, numHilos);

        ExecutorService executor = Executors.newFixedThreadPool(hilos);
        int tamanoLote = (int) Math.ceil((double) total / hilos);

        try {
            for (int i = 0; i < hilos; i++) {
                final int idH = i;
                int inicio = i * tamanoLote;
                if (inicio >= total) {
                    break;
                }
                int fin = Math.min(inicio + tamanoLote, total);
                List<Map.Entry<Double, String>> subLista = lista.subList(inicio, fin);

                executor.submit(() -> descargarLote(idH, subLista, rutaBase,
                        exitos, globales, total, vip, faltantes, cb));
            }

            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.HOURS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        } finally {
            if (!executor.isShutdown()) {
                executor.shutdownNow();
            }
        }

        return new ResultadoLote(exitos.get(), total, vip, faltantes);
    }

    /** Bucle de un hilo sobre su sub-lista: reintentos, VIP y reciclado del navegador. */
    private void descargarLote(int idH, List<Map.Entry<Double, String>> subLista, String rutaBase,
            AtomicInteger exitos, AtomicInteger globales, int total,
            Set<Double> vip, Set<Double> faltantes, ProgresoDescarga cb) {

        Scraper scraper = fabricaScraper.get();
        ExtractorContenido extractor = fabricaExtractor.get();
        EscritorArchivo escritor = fabricaEscritor.get();
        int hechosEnHilo = 0;
        int totalEnHilo = subLista.size();

        try {
            for (Map.Entry<Double, String> entrada : subLista) {
                double numCap = entrada.getKey();
                String urlCap = entrada.getValue();

                cb.capituloIniciado(idH, numCap, hechosEnHilo, totalEnHilo, globales.get(), total);

                boolean exito = false;
                boolean esVip = false;
                // Reintentos solo para fallos transitorios. Un VIP corta el bucle al instante
                // (no gasta reintentos). Se recrea el navegador si hubo excepción.
                for (int intento = 1; intento <= MAX_INTENTOS && !exito && !esVip; intento++) {
                    try {
                        scraper.navegarA(urlCap);
                        Scraper.ResultadoCapitulo r = scraper.obtenerContenidoOVip();
                        if (r.vip) {
                            esVip = true;
                        } else if (r.html != null) {
                            Capitulo capitulo = extractor.extraer(r.html, scraper.getConfig());
                            if (capitulo != null) {
                                escritor.guardarCapitulo(capitulo, rutaBase, numCap);
                                exitos.incrementAndGet();
                                exito = true;
                            }
                        }
                    } catch (Exception e) {
                        // Navegador flojo/muerto: recrear antes de reintentar.
                        scraper.cerrar();
                        scraper = fabricaScraper.get();
                    }
                    if (!exito && !esVip && intento < MAX_INTENTOS) {
                        log.debug("Cap #{} no disponible, reintento {}/{}...", numCap, intento + 1, MAX_INTENTOS);
                    }
                }

                if (esVip) {
                    log.debug("Cap #{} es VIP (bloqueado), se omite.", numCap);
                    vip.add(numCap);
                } else if (!exito) {
                    // A DEBUG (archivo) para no romper la barra de progreso de la CLI; el resumen
                    // final y FALTANTES.txt enumeran los fallidos, y el .log guarda la URL.
                    log.debug("Cap #{} no se pudo bajar tras {} intentos: {}", numCap, MAX_INTENTOS, urlCap);
                    faltantes.add(numCap);
                }

                hechosEnHilo++;
                globales.incrementAndGet();
                cb.capituloTerminado(idH, numCap, hechosEnHilo, totalEnHilo, globales.get(), total);

                if (hechosEnHilo % REFRESCO_CADA == 0) {
                    log.debug("[Hilo {}] Refrescando navegador para liberar RAM...", idH);
                    scraper.cerrar();
                    scraper = fabricaScraper.get();
                }
            }
            cb.hiloTerminado(idH);
        } catch (Exception ex) {
            log.error("[Hilo {}] Error inesperado en el lote: {}", idH, ex.getMessage(), ex);
        } finally {
            scraper.cerrar();
        }
    }
}
