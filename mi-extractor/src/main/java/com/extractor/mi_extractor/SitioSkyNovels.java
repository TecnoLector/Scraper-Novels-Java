package com.extractor.mi_extractor;

public class SitioSkyNovels extends SitioWebConfig {

    @Override
    public String getNombreSitio() {
        return "SkyNovels (Oficial)";
    }

    @Override
    public boolean esSoportado(String url) {
        // IMPORTANTE: Debe llamarse 'soportaUrl' para coincidir con GestorSitios
        return url != null && url.contains("skynovels.net");
    }

    @Override
    public String getSelectorContenido() {
        // Intenta capturar el contenido principal o fallback genéricos
        return "div.skn-chp-chapter-content, div.chapter-content, div.entry-content";
    }

    @Override
    public String getSelectorTitulo() {
        return "h1, .skn-chp-chapter-title";
    }

    @Override
    public String getSelectorContenedorIndice() {
    // Debe incluir mat-expansion-panel para que Selenium espere a los volúmenes
    return "div.skn-nvl-chp-element, mat-expansion-panel"; 
}
    
    @Override
    public String getSelectorElementoLista() {
        // Captura la fila completa del capítulo O directamente el enlace si la estructura falla
        return "div.skn-nvl-chp-element, a[href*='/capitulo/']"; 
    }

    @Override
    public String getSelectorEnlaceCapitulo() {
        // Busca cualquier enlace que tenga '/capitulo/' en su ruta (muy seguro)
        return "a";
    }

    @Override
    public String getSelectorNumeroCapitulo() {
        // Selector específico para el número, si falla, IndiceExtractor usará el texto del enlace
        return "div.skn-nvl-chp-element-chp-number-index";
    }
}