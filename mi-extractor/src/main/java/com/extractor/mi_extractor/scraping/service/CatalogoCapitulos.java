package com.extractor.mi_extractor.scraping.service;

import java.util.Map;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Recorre el índice de una novela (paginado incluido) con el {@link Scraper} y devuelve el
 * mapa completo de capítulos (número -> URL). Extraído de {@link DescargaWebService} sin cambios
 * de comportamiento; lo usan CLI y web. El progreso va al log (no a stdout) para no ensuciar
 * la consola del servidor en las descargas web.
 */
public class CatalogoCapitulos {

    private static final Logger log = LoggerFactory.getLogger(CatalogoCapitulos.class);

    private CatalogoCapitulos() {
    }

    public static Map<Double, String> obtener(String urlPortada) {
        log.info("Obteniendo lista de capítulos...");
        Map<Double, String> mapaTotal = new TreeMap<>();

        Scraper scraper = new Scraper();
        IndiceExtractor extractor = new IndiceExtractor();

        String urlActual = urlPortada;
        int pagina = 1;

        try {
            while (urlActual != null) {
                log.info(">>> Analizando índice - Página {}...", pagina);
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
                    log.warn("No se encontraron capítulos en la página {}.", pagina);
                    if (pagina == 1)
                        break;
                } else {
                    mapaTotal.putAll(capitulosPagina);
                    log.info("    -> Encontrados {} caps. (Total acumulado: {})", capitulosPagina.size(),
                            mapaTotal.size());
                }

                String urlSiguiente = extractor.obtenerSiguientePagina(htmlIndice, urlActual);

                if (urlSiguiente != null && !urlSiguiente.equals(urlActual)) {
                    log.info(">>> Detectada siguiente página: {}", urlSiguiente);
                    urlActual = urlSiguiente;
                    pagina++;
                } else {
                    log.info(">>> Fin del índice (No se detectaron más páginas).");
                    urlActual = null;
                }
            }
        } finally {
            scraper.cerrar(); // SIEMPRE cerrar, aunque el navegador haya muerto a mitad del índice.
        }
        return mapaTotal;
    }
}
