package com.extractor.mi_extractor.scraping.service;

import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Recorre el índice de una novela (paginado incluido) con el {@link Scraper} y devuelve el
 * mapa completo de capítulos (número -> URL). Lo usan CLI y web.
 *
 * <p>El progreso se entrega por un callback: el CLI le pasa {@code System.out::println} para
 * ver el avance limpio en consola; el web pasa un no-op (su progreso viaja por el JSON del
 * trabajo). Además todo queda en el log a nivel debug (fichero), sin ensuciar la consola.
 */
public class CatalogoCapitulos {

    private static final Logger log = LoggerFactory.getLogger(CatalogoCapitulos.class);

    private CatalogoCapitulos() {
    }

    /** Variante sin progreso (lo usa el web, que informa por su propio canal). */
    public static Map<Double, String> obtener(String urlPortada) {
        return obtener(urlPortada, m -> {
        });
    }

    public static Map<Double, String> obtener(String urlPortada, Consumer<String> progreso) {
        Map<Double, String> mapaTotal = new TreeMap<>();

        // Fallo rápido: si el sitio no está soportado, evitamos abrir Chrome y el timeout.
        if (!GestorSitios.esUrlSoportada(urlPortada)) {
            avisar(progreso, "Sitio no soportado (no está en la lista de webs conocidas): " + urlPortada);
            return mapaTotal;
        }

        avisar(progreso, "Obteniendo lista de capítulos...");

        Scraper scraper = new Scraper();
        IndiceExtractor extractor = new IndiceExtractor();

        String urlActual = urlPortada;
        int pagina = 1;

        try {
            while (urlActual != null) {
                avisar(progreso, ">>> Analizando índice - Página " + pagina + "...");
                scraper.navegarA(urlActual);

                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                }

                String htmlIndice = scraper.obtenerHtmlDeIndice();
                if (htmlIndice == null)
                    break;

                Map<Double, String> capitulosPagina = extractor.extraerEnlaces(htmlIndice, urlActual);

                if (capitulosPagina.isEmpty()) {
                    avisar(progreso, "No se encontraron capítulos en la página " + pagina + ".");
                    if (pagina == 1)
                        break;
                } else {
                    mapaTotal.putAll(capitulosPagina);
                    avisar(progreso, "    -> Encontrados " + capitulosPagina.size() + " caps. (Total acumulado: "
                            + mapaTotal.size() + ")");
                }

                String urlSiguiente = extractor.obtenerSiguientePagina(htmlIndice, urlActual);

                if (urlSiguiente != null && !urlSiguiente.equals(urlActual)) {
                    avisar(progreso, ">>> Detectada siguiente página: " + urlSiguiente);
                    urlActual = urlSiguiente;
                    pagina++;
                } else {
                    avisar(progreso, ">>> Fin del índice (No se detectaron más páginas).");
                    urlActual = null;
                }
            }
        } finally {
            scraper.cerrar(); // SIEMPRE cerrar, aunque el navegador haya muerto a mitad del índice.
        }
        return mapaTotal;
    }

    /** Entrega el mensaje al callback (UI del llamador) y lo deja en el log a nivel debug. */
    private static void avisar(Consumer<String> progreso, String mensaje) {
        if (progreso != null) {
            progreso.accept(mensaje);
        }
        log.debug("{}", mensaje);
    }
}
