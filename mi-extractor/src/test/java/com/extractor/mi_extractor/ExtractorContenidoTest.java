package com.extractor.mi_extractor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Tests de lógica pura de {@link ExtractorContenido} (sin navegador): parseo del HTML de un
 * capítulo con Jsoup, limpieza de basura (script/estilos) y detección del enlace "siguiente".
 * Es la red de seguridad para poder trocear las clases grandes sin romper la extracción.
 */
class ExtractorContenidoTest {

    private final ExtractorContenido extractor = new ExtractorContenido();

    @Test
    void extraeTituloYContenidoYLimpiaBasura() {
        String html = "<html><head><title>pestaña</title></head><body>"
                + "<h1 class=\"entry-title\">Capítulo 1: El Inicio</h1>"
                + "<div class=\"entry-content\">"
                + "<p>Hola mundo.</p>"
                + "<script>codigoMalicioso();</script>"
                + "<style>.x{}</style>"
                + "</div></body></html>";

        Capitulo cap = extractor.extraer(html, new SitioNovelasLigera());

        assertNotNull(cap, "debe extraer el capítulo");
        assertEquals("Capítulo 1: El Inicio", cap.getTitulo());
        assertTrue(cap.getContenidoHtml().contains("Hola mundo."), "conserva el texto del capítulo");
        assertFalse(cap.getContenidoHtml().contains("codigoMalicioso"), "elimina los <script>");
        assertFalse(cap.getContenidoHtml().toLowerCase().contains("<style"), "elimina los <style>");
    }

    @Test
    void detectaTituloPorPatronCuandoNoHayConfig() {
        // Sin config válida: cae a los selectores candidatos y al patrón "Capítulo N".
        String html = "<html><body>"
                + "<h2 class=\"title\">Capítulo 7</h2>"
                + "<div class=\"entry-content\"><p>Texto del siete.</p></div>"
                + "</body></html>";

        Capitulo cap = extractor.extraer(html, null);

        assertNotNull(cap);
        assertEquals("Capítulo 7", cap.getTitulo());
        assertTrue(cap.getContenidoHtml().contains("Texto del siete."));
    }

    @Test
    void detectaEnlaceSiguienteCapitulo() {
        String base = "https://ejemplo.com/novela/cap-1/";
        String html = "<html><body>"
                + "<a href=\"/novela/cap-0/\">Anterior</a>"
                + "<a href=\"/novela/cap-2/\">Siguiente</a>"
                + "</body></html>";

        String siguiente = extractor.obtenerEnlaceSiguienteCapitulo(html, base, null);

        assertEquals("https://ejemplo.com/novela/cap-2/", siguiente);
    }

    @Test
    void sinEnlaceSiguienteDevuelveNull() {
        String base = "https://ejemplo.com/novela/cap-1/";
        String html = "<html><body><a href=\"/novela/cap-0/\">Anterior</a></body></html>";

        assertEquals(null, extractor.obtenerEnlaceSiguienteCapitulo(html, base, null));
    }
}
