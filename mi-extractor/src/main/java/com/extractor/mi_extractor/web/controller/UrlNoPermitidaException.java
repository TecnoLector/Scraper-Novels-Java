package com.extractor.mi_extractor.web.controller;

/** URL rechazada por el validador de seguridad (esquema, allowlist o dirección no pública). */
public class UrlNoPermitidaException extends RuntimeException {
    public UrlNoPermitidaException(String mensaje) {
        super(mensaje);
    }
}
