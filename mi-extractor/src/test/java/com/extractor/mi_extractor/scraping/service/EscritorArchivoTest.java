package com.extractor.mi_extractor.scraping.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EscritorArchivoTest {

    @Test
    void escapaLasCincoEntidadesXml() {
        assertEquals("&amp;&lt;&gt;&quot;&apos;", EscritorArchivo.escaparXml("&<>\"'"));
    }

    @Test
    void escapaUnTituloRealConAmpersand() {
        assertEquals("Tom &amp; Jerry &lt;3", EscritorArchivo.escaparXml("Tom & Jerry <3"));
    }

    @Test
    void dejaIgualElTextoSinCaracteresEspeciales() {
        assertEquals("Capitulo 12: El viaje", EscritorArchivo.escaparXml("Capitulo 12: El viaje"));
    }

    @Test
    void nullSeConvierteEnCadenaVacia() {
        assertEquals("", EscritorArchivo.escaparXml(null));
    }

    @Test
    void nombreArchivoUsaCuatroDigitosYSufijoDeParte() {
        assertEquals("Capitulo0001.xhtml", EscritorArchivo.nombreArchivo(1));
        assertEquals("Capitulo0042.xhtml", EscritorArchivo.nombreArchivo(42));
        assertEquals("Capitulo0002_5.xhtml", EscritorArchivo.nombreArchivo(2.5));
    }

    @Test
    void rutaDestinoVaDentroDeOebpsText() {
        Path ruta = EscritorArchivo.rutaDestino("C:/novelas/MiNovela", 7);
        assertTrue(ruta.endsWith(Path.of("OEBPS", "Text", "Capitulo0007.xhtml")),
                "debe guardar en OEBPS/Text con el nombre del capítulo");
    }

    @Test
    void yaDescargadoDetectaElArchivoExistente(@TempDir Path base) throws Exception {
        // Aún no existe -> false.
        assertFalse(EscritorArchivo.yaDescargado(base.toString(), 3));

        // Creamos el .xhtml del cap. 3 y ahora sí debe detectarlo.
        Path destino = EscritorArchivo.rutaDestino(base.toString(), 3);
        Files.createDirectories(destino.getParent());
        Files.writeString(destino, "<html/>");
        assertTrue(EscritorArchivo.yaDescargado(base.toString(), 3));
    }
}
