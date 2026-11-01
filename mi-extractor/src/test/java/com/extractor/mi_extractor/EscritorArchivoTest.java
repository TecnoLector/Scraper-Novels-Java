package com.extractor.mi_extractor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

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
}
