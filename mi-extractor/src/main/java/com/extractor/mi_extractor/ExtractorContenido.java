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
    
    private final String[] selectoresTituloCandidatos = {
        "h1[class*='title']", "h2[class*='title']", "h3.text-center",
        "p > strong","strong", "h1", "h2", "h3", "h4","b"
    };
    
    private final String[] selectoresContenedor = {
        "div.skn-chp-chapter-content", "div#chapter-content", "div.entry-content", "article"
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
                System.err.println("Selector de contenido inválido en config."); 
            }

            try {
                String selTit = config.getSelectorTitulo();
                if (selTit != null) {
                    tituloElement = doc.selectFirst(selTit);
                }
            } catch (Exception e) { 
                System.err.println("Selector de título inválido en config.");
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
            return new Capitulo(titulo, contenidoHtml);
        }
        
        System.err.println("Error: No se pudo extraer capítulo. HTML parcial guardado.");
        guardarHtmlDeError(html);
        return null;
    }
    
    private void guardarHtmlDeError(String html) {
        try {
            String nombreArchivoDebug = "debug_pagina_fallida.html";
            Files.writeString(Paths.get(nombreArchivoDebug), html);
            System.out.println("Se ha guardado el HTML de la página fallida en: " + nombreArchivoDebug);
        } catch (IOException e) {
            System.err.println("No se pudo guardar el archivo de depuración.");
        }
    }

    
}