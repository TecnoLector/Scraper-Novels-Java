package com.extractor.mi_extractor.web.controller;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * Tests sin red: solo cubren los rechazos que no dependen de DNS externo
 * (vacío, esquema, allowlist y loopback 127.0.0.1, que resuelve localmente).
 */
class UrlSafetyValidatorTest {

    private final UrlSafetyValidator validador = new UrlSafetyValidator();

    @Test
    void rechazaUrlVacia() {
        assertThrows(UrlNoPermitidaException.class, () -> validador.validar(""));
        assertThrows(UrlNoPermitidaException.class, () -> validador.validar(null));
    }

    @Test
    void rechazaEsquemaNoHttp() {
        assertThrows(UrlNoPermitidaException.class,
                () -> validador.validar("ftp://www.skynovels.net/x"));
    }

    @Test
    void rechazaSitioNoSoportado() {
        assertThrows(UrlNoPermitidaException.class,
                () -> validador.validar("http://malo-no-soportado.com/"));
    }

    @Test
    void rechazaMetadataAws() {
        assertThrows(UrlNoPermitidaException.class,
                () -> validador.validar("http://169.254.169.254/latest/meta-data/"));
    }

    @Test
    void bloqueaLoopbackAunqueElAllowlistPasePorSubstring() {
        // La IP interna debe atrapar el bypass del allowlist por substring.
        assertThrows(UrlNoPermitidaException.class,
                () -> validador.validar("http://127.0.0.1/skynovels.net"));
    }
}
