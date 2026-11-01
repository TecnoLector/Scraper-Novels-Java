package com.extractor.mi_extractor;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GestorSitiosTest {

    @Test
    void reconoceSitiosSoportados() {
        assertTrue(GestorSitios.esUrlSoportada("https://www.skynovels.net/novela/x"));
        assertTrue(GestorSitios.esUrlSoportada("https://novelasligeras.net/algo"));
        assertTrue(GestorSitios.esUrlSoportada("https://misitio.blogspot.com/p/indice.html"));
    }

    @Test
    void rechazaSitiosNoSoportados() {
        assertFalse(GestorSitios.esUrlSoportada("https://www.google.com/"));
        assertFalse(GestorSitios.esUrlSoportada("https://malo-no-soportado.com/"));
    }

    @Test
    void rechazaNull() {
        assertFalse(GestorSitios.esUrlSoportada(null));
    }
}
