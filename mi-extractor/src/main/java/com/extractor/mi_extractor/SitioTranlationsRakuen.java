package com.extractor.mi_extractor;

public class SitioTranlationsRakuen extends SitioWebConfig {

    @Override
    public String getNombreSitio() {
        return "Blogspot - Chtholly Nota Seniorious";
    }

    @Override
    public boolean esSoportado(String url) {
        // Validación segura y directa para saber si es de Blogger
        return url != null && url.toLowerCase().contains("blogspot.com");
    }

    @Override
    public String getSelectorContenido() {
        // En Blogger, el texto de la novela se encuentra en este contenedor principal
        return "div.post-body.entry-content"; 
    }

    @Override
    public String getSelectorTitulo() {
        return "h3.post-title, h1.post-title, .post-title"; 
    }

    @Override
    public String getSelectorContenedorIndice() {
        // El índice de capítulos se encuentra dentro del mismo cuerpo del post
        return "div.post-body.entry-content";
    }

    @Override
    public String getSelectorElementoLista() {
        // Añadimos 'h3' para que el extractor lea el contexto del capítulo antes de los enlaces 'p'
        return "h3, p"; 
    }

    @Override
    public String getSelectorEnlaceCapitulo() {
        // El enlace específico para navegar a cada parte o capítulo
        return "a"; 
    }

    @Override
    public String getSelectorNumeroCapitulo() {
        // Se mantiene null para que el sistema utilice las expresiones regulares configuradas
        return null; 
    }

    @Override
    public String getSelectorSiguientePaginaIndice() {
        // Al ser un índice de página única en Blogger, no se requiere selector de paginación
        return null;
    }
}