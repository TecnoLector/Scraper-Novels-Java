package com.extractor.mi_extractor;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ExtractorContenido {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ExtractorContenido.class);

    private final String[] selectoresTituloCandidatos = {
        "h1[class*='title']", "h2[class*='title']", "h3.text-center",
        "p > strong","strong", "h1", "h2", "h3", "h4","b"
    };
    
    private final String[] selectoresContenedor = {
        "div.skn-chp-chapter-content.ng-star-inserted",
        "div.skn-chp-chapter-content",
        "div#chapter-content", 
        "div.entry-content", 
        "article"
    };

    public Capitulo extraer(String html, SitioWebConfig config) {
        Document doc = Jsoup.parse(html);
        
        Element tituloElement = null;
        Element contenidoElement = null;

        if (config != null) {
            try {
                String selCont = config.getSelectorContenido();
                if (selCont != null) {
                    contenidoElement = doc.selectFirst(selCont);
                }
            } catch (Exception e) { 
                log.warn("Selector de contenido inválido en config.");
            }

            try {
                String selTit = config.getSelectorTitulo();
                if (selTit != null) {
                    tituloElement = doc.selectFirst(selTit);
                }
            } catch (Exception e) { 
                log.warn("Selector de título inválido en config.");
            }
        }

        if (tituloElement == null) {
            Pattern patronTituloValido = Pattern.compile("Cap[ií]tulo\\s*\\d+", Pattern.CASE_INSENSITIVE);
            
            for (String selector : selectoresTituloCandidatos) {
                for (Element candidato : doc.select(selector)) {
                    if (candidato != null && candidato.hasText()) {
                        Matcher matcher = patronTituloValido.matcher(candidato.text());
                        if (matcher.find()) {
                            tituloElement = candidato;
                            break;
                        }
                    }
                }
                if (tituloElement != null) break;
            }
        }

        if (contenidoElement == null) {
            for (String selector : selectoresContenedor) {
                contenidoElement = doc.selectFirst(selector);
                if (contenidoElement != null) {
                    break;
                }
            }
        }

        if (tituloElement == null) {
            tituloElement = doc.selectFirst("title");
        }

        if (tituloElement != null && contenidoElement != null) {
            String titulo = tituloElement.text();
            
            contenidoElement.select("script, style, .publi, .adsb30, .wp-post-navigation, .zeno_font_resizer_container, .osny-nightmode, .saboxplugin-wrap, miad-block2, miad-block3, script, style, .publi").remove(); 
            
            try {
                if (contenidoElement.children().contains(tituloElement)) {
                    tituloElement.remove();
                } else {
                    for(Element el : contenidoElement.getAllElements()) {
                        if(el.text().trim().equals(titulo)) {
                            el.remove();
                            break;
                        }
                    }
                }
            } catch (Exception e) { }
            
            String contenidoHtml = contenidoElement.html().replace("&nbsp;", "&#160;");
            
            // VALIDACIÓN: Verificar que el contenido NO esté vacío
            if (contenidoHtml.trim().isEmpty()) {
                log.warn("El contenido extraído está vacío. Guardando HTML de error...");
                guardarHtmlDeError(html);
                return null;
            }
            
            return new Capitulo(titulo, contenidoHtml);
        }
        
        // 🔍 DIAGNÓSTICO MEJORADO
        log.warn("No se pudo extraer capítulo (titulo encontrado={}, contenido encontrado={}). Guardando HTML de error para diagnóstico...",
                tituloElement != null, contenidoElement != null);
        guardarHtmlDeError(html);
        return null;
    }
    
    private void guardarHtmlDeError(String html) {
        try {
            String nombreArchivoDebug = "debug_pagina_fallida.html";
            Files.writeString(Paths.get(nombreArchivoDebug), html);
            log.info("HTML de la página fallida guardado en: {}", nombreArchivoDebug);
        } catch (IOException e) {
            log.warn("No se pudo guardar el archivo de depuración.");
        }
    }

    public String obtenerEnlaceSiguienteCapitulo(String html, String urlActual, SitioWebConfig config) {
        Document doc = Jsoup.parse(html, urlActual);
        
        for (Element enlace : doc.select("a[href]")) {
            String textoEnlace = enlace.text().toLowerCase().trim();
            if (textoEnlace.contains("siguiente") || textoEnlace.contains("next") || 
                textoEnlace.equals("sig >") || textoEnlace.equals("siguiente ->") ||
                textoEnlace.contains("siguiente capitulo")) {
                
                String urlSiguiente = enlace.absUrl("href"); 
                
                if (!urlSiguiente.isEmpty() && !urlSiguiente.equals(urlActual) && !urlSiguiente.contains("#")) {
                    return urlSiguiente;
                }
            }
        }
        return null;
    }

    
}