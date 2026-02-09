package com.extractor.mi_extractor;

public class SitioNovelasLigera extends SitioWebConfig {

    @Override
    public String getNombreSitio() {
        return "Novelas Ligera";
    }

    @Override
    public boolean esSoportado(String url) {
        return url.toLowerCase().contains("novelasligera");
    }

    @Override
    public String getSelectorContenido() {
        return "div.entry-content";
    }

    @Override
    public String getSelectorTitulo() {
        return "h1.entry-title";
    }

    @Override
    public String getSelectorContenedorIndice() {
        return "ul.lcp_catlist";
    }

    @Override
    public String getSelectorElementoLista() {
        return "ul.lcp_catlist li";
    }

    @Override
    public String getSelectorEnlaceCapitulo() {
        return "a";
    }

    @Override
    public String getSelectorNumeroCapitulo() {
        return "a";
    }
}