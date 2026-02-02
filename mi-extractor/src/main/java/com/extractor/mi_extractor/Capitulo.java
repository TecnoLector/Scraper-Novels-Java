package com.extractor.mi_extractor;

public class Capitulo {
    private final String titulo;
    private final String contenidoHtml;

    public Capitulo(String titulo, String contenidoHtml) {
        this.titulo = titulo;
        this.contenidoHtml = contenidoHtml;
    }

    public String getTitulo() {
        return titulo;
    }

    public String getContenidoHtml() {
        return contenidoHtml;
    }
}