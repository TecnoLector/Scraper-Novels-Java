package com.extractor.mi_extractor.scraping.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.extractor.mi_extractor.scraping.service.IndiceExtractor;

/**
 * Lógica pura de {@link SitioNovelaEnEspanol} (sin navegador ni red): reconstrucción del HTML del
 * índice a partir del JSON del endpoint AJAX del tema, y su numeración vía {@link IndiceExtractor}.
 * El JSON de ejemplo imita la respuesta real: el primer item es la propia novela (sin "capitulo"
 * en el slug) y debe descartarse; los capítulos llevan "... Capítulo N: ..." en el título.
 */
class SitioNovelaEnEspanolTest {

    private static final String URL_PORTADA =
            "https://novelaenespanol.com/mi-bellisima-presidenta/novela-ligera/mi-bellisima-presidenta/";

    // Respuesta recortada del endpoint real (orden descendente, como la sirve el sitio).
    private static final String JSON = "["
            + "{\"ID\":\"1\",\"post_name\":\"mi-bellisima-presidenta\",\"post_title\":\"Mi bellísima presidenta\"},"
            + "{\"ID\":\"2\",\"post_name\":\"my-extremely-beautiful-president-capitulo-3-el-poder\","
            + "\"post_title\":\"Mi bellísima presidenta Capítulo 3: El poder\"},"
            + "{\"ID\":\"3\",\"post_name\":\"my-extremely-beautiful-president-capitulo-2-algo\","
            + "\"post_title\":\"Mi bellísima presidenta Capítulo 2: Algo\"},"
            + "{\"ID\":\"4\",\"post_name\":\"my-extremely-beautiful-president-capitulo-1-renacimiento-del-bano\","
            + "\"post_title\":\"Mi bellísima presidenta Capítulo 1: Renacimiento del baño\"}"
            + "]";

    private final SitioNovelaEnEspanol sitio = new SitioNovelaEnEspanol();

    @Test
    void esSoportadoReconoceElDominio() {
        assertTrue(sitio.esSoportado(URL_PORTADA));
        assertFalse(sitio.esSoportado("https://www.skynovels.net/novelas/377/engane-a-un-dios"));
    }

    @Test
    void slugSaleDelPrimerSegmentoDelPath() {
        assertEquals("mi-bellisima-presidenta", SitioNovelaEnEspanol.slugDesdeUrl(URL_PORTADA));
    }

    @Test
    void reconstruyeSoloCapitulosYConLaUrlCorrecta() throws Exception {
        String html = sitio.construirIndiceHtml(JSON, "mi-bellisima-presidenta");

        // La propia novela (sin "capitulo" en el slug) se descarta.
        assertFalse(html.contains("href=\"/mi-bellisima-presidenta/novela-ligera/mi-bellisima-presidenta/\""),
                "la portada no debe aparecer como capítulo");
        // Un capítulo arma su URL como /slug/novela-ligera/post_name/
        assertTrue(html.contains(
                "href=\"/mi-bellisima-presidenta/novela-ligera/my-extremely-beautiful-president-capitulo-1-renacimiento-del-bano/\""),
                "debe construir la URL del capítulo 1");
    }

    @Test
    void indiceExtractorNumeraLosCapitulosDesdeElTitulo() throws Exception {
        String html = sitio.construirIndiceHtml(JSON, "mi-bellisima-presidenta");

        Map<Double, String> mapa = new IndiceExtractor().extraerEnlaces(html, URL_PORTADA);

        assertEquals(3, mapa.size(), "3 capítulos (la portada queda fuera)");
        assertTrue(mapa.containsKey(1.0) && mapa.containsKey(2.0) && mapa.containsKey(3.0));
        assertTrue(mapa.get(1.0).endsWith("-capitulo-1-renacimiento-del-bano/"),
                "el cap. 1 apunta a su URL absoluta");
    }
}
