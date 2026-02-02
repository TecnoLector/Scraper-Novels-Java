package com.extractor.mi_extractor;

public class SitioSkyNovels extends SitioWebConfig {

    @Override
    public String getNombreSitio() {
        return "Sitio Default (Skn)";
    }

    @Override
    public boolean esSoportado(String url) {
        return true; 
    }

    @Override
    public String getSelectorContenido() {
        return "div.skn-chp-chapter-content";
    }

    @Override
    public String getSelectorTitulo() {
        return "h1";
    }
    @Override
    public String getSelectorContenedorIndice() {
        return "div.skn-nvl-chp-element-chp-number-index"; 
    }
    
    @Override
    public String getSelectorElementoLista() {
        return "div.skn-nvl-chp-element"; 
    }

    @Override
    public String getSelectorEnlaceCapitulo() {
        return "a.skn-link";
    }

    @Override
    public String getSelectorNumeroCapitulo() {
        return "div.skn-nvl-chp-element-chp-number-index";
    }
}