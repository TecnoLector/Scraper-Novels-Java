package com.extractor.mi_extractor;

public class SitioNOVA extends SitioWebConfig {

    @Override
    public String getNombreSitio() {
        return "NOVA";
    }

    @Override
    public boolean esSoportado(String url) {
        return url != null && url.toLowerCase().contains("novelasligeras.net");
    }

    @Override
    public String getSelectorContenido() {
        // Selector estándar de WordPress para el texto de la novela
        return "div.entry-content, div.post-content, div.td-post-content";
    }

    @Override
    public String getSelectorTitulo() {
        return "h1.entry-title, h1";
    }

    @Override
    public String getSelectorContenedorIndice() {
        // Esperamos a que cargue cualquiera de los contenedores de pestañas
        return "div.wpb_tour_tabs_wrapper, div.wpb_wrapper";
    }

    @Override
    public String getSelectorElementoLista() {
        // Cada capítulo está dentro de un div con clase 'post-content' dentro de la tabla
        return "div.post-content";
    }

    @Override
    public String getSelectorEnlaceCapitulo() {
        // El enlace directo
        return "a";
    }

    @Override
    public String getSelectorNumeroCapitulo() {
        // El número está en el texto del enlace (ej: "Capítulo 1: ...")
        return "a";
    }
    
    @Override
    public String getSelectorSiguientePaginaIndice() {
        // Esta web carga todos los capítulos en una sola página (aunque use pestañas),
        // así que no necesitamos paginación.
        return null;
    }
}