package com.extractor.mi_extractor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Valida la pieza pura que ADR-004 mudó desde Scraper a SitioSkyNovels: reconstruirDomDesdeEnlaces.
 * No abre Chrome; comprueba la forma del DOM y que IndiceExtractor lo puede parsear.
 */
class SitioSkyNovelsTest {

    private static final String INDICE = "https://www.skynovels.net/novelas/53/x";

    @Test
    void reconstruyeDomEnvolviendoCadaEnlace() {
        SitioSkyNovels sitio = new SitioSkyNovels();
        List<String> enlaces = List.of(
                "<a href=\"https://www.skynovels.net/novelas/53/x/101/\">Capítulo 1</a>",
                "<a href=\"https://www.skynovels.net/novelas/53/x/102/\">Capítulo 2</a>",
                "<a href=\"https://www.skynovels.net/novelas/53/x/103/\">Capítulo 3</a>");

        String dom = sitio.reconstruirDomDesdeEnlaces(enlaces);

        assertTrue(dom.contains("<mat-expansion-panel>"), "debe envolver en el panel que espera el parser");
        assertEquals(3, contar(dom, "class=\"skn-nvl-chp-element\""), "un wrapper por enlace");
        assertTrue(dom.contains("/101/") && dom.contains("/103/"), "conserva las URLs");
    }

    @Test
    void domReconstruidoLoParseaIndiceExtractor() {
        SitioSkyNovels sitio = new SitioSkyNovels();
        List<String> enlaces = List.of(
                "<a href=\"https://www.skynovels.net/novelas/53/x/101/\">Capítulo 1</a>",
                "<a href=\"https://www.skynovels.net/novelas/53/x/102/\">Capítulo 2</a>",
                "<a href=\"https://www.skynovels.net/novelas/53/x/103/\">Capítulo 3</a>");

        String dom = sitio.reconstruirDomDesdeEnlaces(enlaces);
        Map<Double, String> caps = new IndiceExtractor().extraerEnlaces(dom, INDICE);

        assertEquals(3, caps.size());
        assertTrue(caps.containsKey(1.0));
        assertTrue(caps.containsKey(2.0));
        assertTrue(caps.containsKey(3.0));
    }

    @Test
    void coleccionVaciaDaUnEsqueletoValidoSinCapitulos() {
        SitioSkyNovels sitio = new SitioSkyNovels();
        String dom = sitio.reconstruirDomDesdeEnlaces(List.of());

        assertTrue(dom.contains("<mat-expansion-panel>"));
        assertFalse(dom.contains("skn-nvl-chp-element"));
        assertEquals(0, new IndiceExtractor().extraerEnlaces(dom, INDICE).size());
    }

    private static int contar(String texto, String sub) {
        int n = 0, i = 0;
        while ((i = texto.indexOf(sub, i)) != -1) {
            n++;
            i += sub.length();
        }
        return n;
    }
}
