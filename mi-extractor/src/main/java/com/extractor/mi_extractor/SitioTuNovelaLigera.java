package com.extractor.mi_extractor;

public class SitioTuNovelaLigera extends SitioWebConfig {

    @Override
    public String getNombreSitio() {
        return "TuNovelaLigera";
    }

    @Override
    public boolean soportaUrl(String url) {
        return url.toLowerCase().contains("tunovelaligera");
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
        return "ul[class*='lcp_catlist']";
    }

    @Override
    public String getSelectorElementoLista() {
        return "ul[class*='lcp_catlist'] > ul > li";
    }

    @Override
    public String getSelectorEnlaceCapitulo() {
        return "a";
    }

    @Override
    public String getSelectorNumeroCapitulo() {
        return "a";
    }
    @Override
    public String getSelectorSiguientePaginaIndice() {
        return "ul[id^='lcp_paginator'] li a.lcp_nextlink, ul[id^='lcp_paginator'] li a:contains(>>)";
    }
}