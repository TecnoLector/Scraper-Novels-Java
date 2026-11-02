package com.extractor.mi_extractor;
import com.extractor.mi_extractor.scraping.model.Capitulo;
import com.extractor.mi_extractor.scraping.util.ConfigScraper;
import com.extractor.mi_extractor.scraping.service.*;
import com.extractor.mi_extractor.epub.service.EstructuraEpubInicializador;
import com.extractor.mi_extractor.common.ReporteFaltantes;
import com.extractor.mi_extractor.common.CapitulosUtil;

import java.util.function.Consumer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

/**
 * Adaptador web (Spring Boot) de descarga: punto de entrada que usa {@link service.EpubService}.
 * La parte CLI vive en {@link DescargaCli}; ambos comparten CatalogoCapitulos, CapitulosUtil,
 * ReporteFaltantes y MotorDescargaParalela.
 */
public class DescargaWebService {

    // ==============================================================
    // MÉTODO EXCLUSIVO PARA LA INTERFAZ WEB DE SPRING BOOT
    // ==============================================================
    public Path descargarWebMultiHilo(int opcion, String url, int hilos, int limite, int inicio, int fin,
            String listaStr, String rutaBaseStr, Consumer<String> progresoWeb) {
        try {
            Path pathBase = Paths.get(rutaBaseStr);
            Files.createDirectories(pathBase);
            EstructuraEpubInicializador.inicializar(pathBase);

            // CASO ESPECIAL: OPCIÓN 6 (Secuencial)
            if (opcion == 6) {
                return descargarSecuencialWeb(url, limite, rutaBaseStr, progresoWeb);
            }

            progresoWeb.accept("15% - 🔍 Analizando índice de la novela...");
            Map<Double, String> todosLosCapitulos = CatalogoCapitulos.obtener(url);

            // FILTRADO DE CAPÍTULOS (Lógica de Menú) — lógica pura extraída y testeable.
            Map<Double, String> filtrados = CapitulosUtil.filtrarCapitulos(todosLosCapitulos, opcion, limite, inicio, fin, listaStr);

            if (filtrados.isEmpty()) {
                progresoWeb.accept("ERROR: ❌ No se encontraron capítulos.");
                return null;
            }

            // --- PREPARACIÓN DEL MOTOR ---
            // Orden ascendente estable para que el troceo por hilos sea determinista.
            java.util.TreeMap<Double, String> ordenados = new java.util.TreeMap<>(filtrados);
            final int aDescargar = ordenados.size();
            int maxNav = ConfigScraper.maxNavegadores();
            if (hilos > maxNav) {
                progresoWeb.accept("Aviso: pediste " + hilos + " navegadores; limitado al tope global de " + maxNav + ".");
            }
            final int numHilosEfectivos = Math.min(Math.min(hilos, aDescargar), maxNav);

            // Estado por hilo para la UI (el JSON de progreso sale idéntico al de antes).
            String[] msgHilos = new String[numHilosEfectivos];
            int[] pctHilos = new int[numHilosEfectivos];
            for (int i = 0; i < numHilosEfectivos; i++) {
                msgHilos[i] = "Iniciando...";
                pctHilos[i] = 0;
            }

            // Arma y emite el JSON multihilo que ya consume el front (mismo formato).
            java.util.function.BiConsumer<Integer, Integer> emitirJson = (hechoGlobal, totalGlobal) -> {
                int gPct = 30 + (int) (((double) hechoGlobal / totalGlobal) * 60);
                StringBuilder sb = new StringBuilder("{ \"tipo\": \"MULTIHILO\", \"globalPct\": ").append(gPct)
                        .append(", \"hilos\": [");
                for (int i = 0; i < numHilosEfectivos; i++) {
                    sb.append("{\"id\": ").append(i + 1).append(", \"pct\": ").append(pctHilos[i])
                            .append(", \"msg\": \"").append(msgHilos[i]).append("\"}");
                    if (i < numHilosEfectivos - 1)
                        sb.append(", ");
                }
                sb.append("]}");
                progresoWeb.accept(sb.toString());
            };

            // ADR-001: el bucle de descarga vive en MotorDescargaParalela (compartido con la CLI).
            MotorDescargaParalela motor = new MotorDescargaParalela();
            MotorDescargaParalela.ResultadoLote res = motor.ejecutar(
                    ordenados, rutaBaseStr, numHilosEfectivos,
                    new MotorDescargaParalela.ProgresoDescarga() {
                        @Override
                        public void capituloIniciado(int hiloIdx, double numCap, int hechosEnHilo,
                                int totalEnHilo, int hechosGlobal, int totalGlobal) {
                            msgHilos[hiloIdx] = "Cap #" + CapitulosUtil.formatearNumeroCap(numCap);
                            pctHilos[hiloIdx] = (totalEnHilo == 0) ? 0
                                    : (int) (((double) hechosEnHilo / totalEnHilo) * 100);
                            emitirJson.accept(hechosGlobal, totalGlobal);
                        }

                        @Override
                        public void capituloTerminado(int hiloIdx, double numCap, int hechosEnHilo,
                                int totalEnHilo, int hechosGlobal, int totalGlobal) {
                            pctHilos[hiloIdx] = (totalEnHilo == 0) ? 0
                                    : (int) (((double) hechosEnHilo / totalEnHilo) * 100);
                            emitirJson.accept(hechosGlobal, totalGlobal);
                        }

                        @Override
                        public void hiloTerminado(int hiloIdx) {
                            msgHilos[hiloIdx] = "LISTO ✅";
                            pctHilos[hiloIdx] = 100;
                            emitirJson.accept(aDescargar, aDescargar);
                        }
                    });

            // Reporte: VIP (bloqueados) y fallos reales, con números exactos.
            if (!res.faltantes.isEmpty() || !res.vip.isEmpty()) {
                ReporteFaltantes.escribir(pathBase, res.faltantes, res.vip);
                StringBuilder resumen = new StringBuilder("95% - Completado.");
                if (!res.vip.isEmpty()) {
                    resumen.append(" VIP (bloqueados): ").append(res.vip.size()).append('.');
                }
                if (!res.faltantes.isEmpty()) {
                    resumen.append(" Sin bajar: ").append(CapitulosUtil.formatearListaCaps(res.faltantes)).append('.');
                }
                resumen.append(" Ver FALTANTES.txt.");
                progresoWeb.accept(resumen.toString());
            }
            return pathBase;

        } catch (Exception e) {
            progresoWeb.accept("ERROR: " + e.getMessage());
            return null;
        }
    }

    /**
     * MÉTODO SECUENCIAL (OPCIÓN 6)
     * Corregido el error de escritura y el typo en progresoWeb.
     */
    private Path descargarSecuencialWeb(String url, int limite, String rutaBase, Consumer<String> progresoWeb) {
        Scraper s = new Scraper();
        ExtractorContenido e = new ExtractorContenido();
        EscritorArchivo w = new EscritorArchivo();
        try {
            String urlAct = url;
            int count = 0;
            int max = (limite > 0) ? limite : Integer.MAX_VALUE;

            while (urlAct != null && count < max) {
                progresoWeb.accept("⚙️ Descargando Cap " + (count + 1));
                s.navegarA(urlAct);
                String html = s.obtenerHtmlDePagina();
                Capitulo cap = e.extraer(html, s.getConfig());

                if (cap == null)
                    break;

                // Bloque de escritura seguro
                try {
                    w.guardarCapitulo(cap, rutaBase, count + 1);
                } catch (Exception writeEx) {
                    System.err.println("Error escribiendo archivo: " + writeEx.getMessage());
                }

                count++;
                urlAct = e.obtenerEnlaceSiguienteCapitulo(html, urlAct, s.getConfig());
            }
            return Paths.get(rutaBase);
        } catch (Exception ex) {
            progresoWeb.accept("ERROR en descarga secuencial: " + ex.getMessage());
            return null;
        } finally {
            s.cerrar();
        }
    }
}
