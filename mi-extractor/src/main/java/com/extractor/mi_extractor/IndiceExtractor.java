package com.extractor.mi_extractor;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class IndiceExtractor {

    public Map<Integer, String> extraerEnlaces(String html, String urlIndice) {
        Map<Integer, String> mapaCapitulos = new HashMap<>();
        Document doc = Jsoup.parse(html);

        SitioWebConfig config = GestorSitios.obtenerConfig(urlIndice);

        String selectorLista = "a"; 
        String selectorEnlace = "a";
        String selectorNumero = "a";

        if (config != null) {
            selectorLista = config.getSelectorElementoLista();
            selectorEnlace = config.getSelectorEnlaceCapitulo();
            selectorNumero = config.getSelectorNumeroCapitulo();
            System.out.println("Usando estrategia: " + config.getNombreSitio());
        } else {
            System.out.println("Modo genérico activado.");
        }

        Elements elementosLista = doc.select(selectorLista);
        System.out.println("Se encontraron " + elementosLista.size() + " elementos candidatos.");

        for (Element elemento : elementosLista) {
            try {
                Element enlace = elemento.selectFirst(selectorEnlace);
                if (enlace == null && elemento.tagName().equals("a")) {
                    enlace = elemento;
                }

                if (enlace != null) {
                    String urlCapitulo = enlace.attr("href");
                    String textoTitulo = "";

                    Element elementoNumero = elemento.selectFirst(selectorNumero);
                    if (elementoNumero != null) {
                        textoTitulo = elementoNumero.text();
                    } else {
                        textoTitulo = enlace.text();
                    }

                    int numeroCap = extraerNumero(textoTitulo);

                    if (numeroCap > 0) {
                        mapaCapitulos.put(numeroCap, urlCapitulo);
                    }
                }
            } catch (Exception e) {
            }
        }

        System.out.println("Se procesaron " + mapaCapitulos.size() + " enlaces a capítulos con éxito.");
        return mapaCapitulos;
    }

    private int extraerNumero(String texto) {
        if (texto == null || texto.isEmpty()) return -1;
        
        Pattern patternEstandar = Pattern.compile("(?i)(?:Cap[íi]tulo|Ch\\.|Vol\\.|Episodio)\\s*(\\d+)");
        Matcher matcherEstandar = patternEstandar.matcher(texto);
        if (matcherEstandar.find()) {
            return Integer.parseInt(matcherEstandar.group(1));
        }
        
        Pattern patternSiglas = Pattern.compile("^[A-Za-z]{2,5}\\s+(\\d+)");
        Matcher matcherSiglas = patternSiglas.matcher(texto);
        if (matcherSiglas.find()) {
            return Integer.parseInt(matcherSiglas.group(1));
        }

        Pattern patternGenerico = Pattern.compile("(\\d+)");
        Matcher matcherGenerico = patternGenerico.matcher(texto);
        if (matcherGenerico.find()) {
            return Integer.parseInt(matcherGenerico.group(1));
        }

        return -1;
    }

    public String obtenerSiguientePagina(String html, String urlBase) {
        Document doc = Jsoup.parse(html, urlBase);
        SitioWebConfig config = GestorSitios.obtenerConfig(urlBase);
        
        String selectorNext = config.getSelectorSiguientePaginaIndice();
        
        if (selectorNext != null) {
            Element enlaceNext = doc.selectFirst(selectorNext);
            if (enlaceNext != null) {
                return enlaceNext.absUrl("href");
            }
        }
        return null;
    }
}