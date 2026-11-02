package com.extractor.mi_extractor;
import com.extractor.mi_extractor.scraping.service.IndiceExtractor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Tests de lógica pura de {@link IndiceExtractor} (sin navegador): numeración de capítulos a
 * partir del HTML del índice, orden ascendente y filtrado de enlaces que no son capítulos.
 */
class IndiceExtractorTest {

    private final IndiceExtractor extractor = new IndiceExtractor();

    // URL de un sitio soportado para que use su estrategia (contenedor ul.lcp_catlist).
    private static final String URL_INDICE = "https://novelasligera.com/novela/mi-novela/";

    @Test
    void numeraCapitulosYLosOrdenaAscendente() {
        String html = "<html><body><ul class=\"lcp_catlist\">"
                + "<li><a href=\"/novela/mi-novela/capitulo-2/\">Capítulo 2</a></li>"
                + "<li><a href=\"/novela/mi-novela/capitulo-1/\">Capítulo 1</a></li>"
                + "<li><a href=\"/novela/mi-novela/capitulo-3/\">Capítulo 3</a></li>"
                + "</ul></body></html>";

        Map<Double, String> mapa = extractor.extraerEnlaces(html, URL_INDICE);

        assertEquals(3, mapa.size());
        assertTrue(mapa.containsKey(1.0) && mapa.containsKey(2.0) && mapa.containsKey(3.0));
        assertTrue(mapa.get(1.0).endsWith("/capitulo-1/"));

        // TreeMap => iteración en orden ascendente de número de capítulo.
        List<Double> orden = new ArrayList<>(mapa.keySet());
        assertEquals(List.of(1.0, 2.0, 3.0), orden);
    }

    @Test
    void ignoraEnlacesQueNoSonCapitulos() {
        String html = "<html><body><ul class=\"lcp_catlist\">"
                + "<li><a href=\"/novela/mi-novela/capitulo-1/\">Capítulo 1</a></li>"
                + "<li><a href=\"/portada.jpg\">Imagen</a></li>"
                + "<li><a href=\"/descarga.pdf\">PDF</a></li>"
                + "</ul></body></html>";

        Map<Double, String> mapa = extractor.extraerEnlaces(html, URL_INDICE);

        assertEquals(1, mapa.size(), "solo el capítulo real debe entrar");
        assertTrue(mapa.containsKey(1.0));
        for (String url : mapa.values()) {
            assertFalse(url.endsWith(".jpg") || url.endsWith(".pdf"));
        }
    }

    @Test
    void mapaVacioSiNoHayEnlaces() {
        String html = "<html><body><ul class=\"lcp_catlist\"></ul></body></html>";
        assertTrue(extractor.extraerEnlaces(html, URL_INDICE).isEmpty());
    }
}
