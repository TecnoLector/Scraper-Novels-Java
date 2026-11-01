package com.extractor.mi_extractor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class MenuExtractorTest {

    // --- extraerNumeroDeTitulo ---

    @Test
    void extraeEnteroDelTitulo() {
        assertEquals(15.0, MenuExtractor.extraerNumeroDeTitulo("Capítulo 15"));
    }

    @Test
    void extraeDecimalDelTitulo() {
        assertEquals(3.5, MenuExtractor.extraerNumeroDeTitulo("Cap 3.5 - Extra"));
    }

    @Test
    void devuelveCeroSiNoHayNumeroOEsNull() {
        assertEquals(0.0, MenuExtractor.extraerNumeroDeTitulo("Prólogo"));
        assertEquals(0.0, MenuExtractor.extraerNumeroDeTitulo(null));
    }

    // --- filtrarCapitulos ---

    private Map<Double, String> muestra() {
        Map<Double, String> m = new LinkedHashMap<>();
        m.put(1.0, "u1");
        m.put(2.0, "u2");
        m.put(3.0, "u3");
        m.put(3.5, "u35");
        m.put(10.0, "u10");
        return m;
    }

    @Test
    void opcion1DevuelveTodos() {
        assertEquals(5, MenuExtractor.filtrarCapitulos(muestra(), 1, 0, 0, 0, "").size());
    }

    @Test
    void opcion2RespetaElLimite() {
        Map<Double, String> r = MenuExtractor.filtrarCapitulos(muestra(), 2, 3, 0, 0, "");
        assertEquals(3, r.size()); // 1.0, 2.0, 3.0 (3.5 queda fuera)
        assertTrue(r.containsKey(3.0));
        assertTrue(!r.containsKey(3.5));
    }

    @Test
    void opcion3DevuelveSoloEseCapitulo() {
        Map<Double, String> r = MenuExtractor.filtrarCapitulos(muestra(), 3, 3, 0, 0, "");
        assertEquals(1, r.size());
        assertTrue(r.containsKey(3.0));
    }

    @Test
    void opcion4FiltraPorRango() {
        Map<Double, String> r = MenuExtractor.filtrarCapitulos(muestra(), 4, 0, 2, 3, "");
        assertEquals(2, r.size()); // 2.0 y 3.0
        assertTrue(!r.containsKey(3.5));
    }

    @Test
    void opcion5FiltraPorListaIgnorandoInexistentes() {
        Map<Double, String> r = MenuExtractor.filtrarCapitulos(muestra(), 5, 0, 0, 0, "1, 3.5, 99");
        assertEquals(2, r.size()); // 1.0 y 3.5; 99 no existe
        assertTrue(r.containsKey(1.0));
        assertTrue(r.containsKey(3.5));
    }

    @Test
    void mapaNullDevuelveVacio() {
        assertTrue(MenuExtractor.filtrarCapitulos(null, 1, 0, 0, 0, "").isEmpty());
    }
}
