package com.extractor.mi_extractor;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.TreeMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class IndiceExtractor {

    public Map<Double, String> extraerEnlaces(String html, String urlIndice) {
        Map<Double, String> mapaCapitulos = new TreeMap<>();
        Document doc = Jsoup.parse(html, urlIndice);

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

        Elements elementosLista;
        if (config != null && config.getSelectorContenedorIndice() != null) {
            Element contenedor = doc.selectFirst(config.getSelectorContenedorIndice());
            if (contenedor != null) {
                elementosLista = contenedor.select(selectorLista);
            } else {
                elementosLista = doc.select(selectorLista);
            }
        } else {
            elementosLista = doc.select(selectorLista);
        }
        System.out.println("Se encontraron " + elementosLista.size() + " elementos candidatos.");

        for (Element elemento : elementosLista) {
            try {
                // Ignorar elementos de lista que no contienen enlaces
                if (elemento.selectFirst(selectorEnlace) == null && !elemento.tagName().equals("a")) {
                    continue;
                }

                Element enlace = elemento.selectFirst(selectorEnlace);
                if (enlace == null && elemento.tagName().equals("a")) {
                    enlace = elemento;
                }

                if (enlace != null) {
                    String urlCapitulo = enlace.absUrl("href");
                    if (urlCapitulo.isEmpty()) urlCapitulo = enlace.attr("href");

                    String baseCapitulo = urlCapitulo.split("\\?")[0].split("#")[0];
                    String baseIndice = urlIndice.split("\\?")[0].split("#")[0];
                    if (baseCapitulo.equals(baseIndice)) {
                        continue;
                    }

                    // FIX 2: Ignorar enlaces de descarga PDF o imágenes sueltas
                    if (urlCapitulo.matches("(?i).*\\.(png|jpg|jpeg|gif|pdf)(\\?.*)?$")) {
                        continue;
                    }

                    // CAMBIO CLAVE: Leer todo el texto (incluyendo cosas fuera del enlace como "1ra Parte")
                    String textoTitulo = elemento.text();

                    double numeroCap = extraerNumero(textoTitulo);
                    if (numeroCap < 0) {
                        numeroCap = extraerNumeroDesdeUrl(urlCapitulo);
                    }

                    if (numeroCap < 0 && (textoTitulo.toLowerCase().contains("prologo") || textoTitulo.toLowerCase().contains("prólogo"))) {
                        numeroCap = 0.0;
                    }

                    // MEMORIA DOM: Subir por el árbol HTML hasta encontrar a qué Capítulo pertenece
                    double capituloContexto = obtenerContexto(elemento);

                    // Lógica para asignar sub-partes correctamente (Ej: 2.01, 2.02, 2.90)
                    if (numeroCap == 99.0 && capituloContexto > 0) {
                        // Las "Parte X" o Extras se van al final del capítulo (ej. 2.90)
                        numeroCap = capituloContexto + 0.90;
                    } else if (numeroCap < 0 && capituloContexto > 0) {
                        // FIX 1: Si el enlace no tiene número (Ej: Cap 5), hereda el número del H3 superior
                        numeroCap = capituloContexto;
                    } else {
                        // FIX 2: Detecta tanto "1ra parte" como "Parte 1"
                        Pattern pSoloParte = Pattern.compile("(?i)(?:(\\d+)[a-z]*\\s*parte|parte\\s*(\\d+))");
                        Matcher mSoloParte = pSoloParte.matcher(textoTitulo);
                        
                        if (mSoloParte.find() && capituloContexto > 0) {
                            String numStr = mSoloParte.group(1) != null ? mSoloParte.group(1) : mSoloParte.group(2);
                            if (numeroCap == Double.parseDouble(numStr)) {
                                numeroCap = capituloContexto + (numeroCap / 100.0);
                            }
                        }
                    }

                    if (numeroCap >= 0) {
                        double numeroFinal = numeroCap;
                        // Sistema anticaídas: si dos partes tienen el mismo número, se le suma 0.01
                        while (mapaCapitulos.containsKey(numeroFinal)) {
                            numeroFinal = Math.round((numeroFinal + 0.01) * 100.0) / 100.0;
                        }
                        mapaCapitulos.put(numeroFinal, urlCapitulo);
                    }
                }
            } catch (Exception e) {
                // Silenciamos errores individuales para continuar
            }
        }

        System.out.println("Se procesaron " + mapaCapitulos.size() + " enlaces a capítulos con éxito.");
        return mapaCapitulos;
    }

    /**
     * Navega hacia arriba y hacia atrás en el código HTML para encontrar el encabezado más cercano.
     */
    private double obtenerContexto(Element elementoInicial) {
        Element current = elementoInicial;
        while (current != null) {
            Element prev = current.previousElementSibling();
            while (prev != null) {
                // 1. Verificamos si el elemento anterior es un título
                if (prev.tagName().matches("h[1-6]") || prev.hasClass("post-title")) {
                    double num = extraerNumeroContexto(prev.text());
                    if (num > 0) return num;
                }
                // 2. Verificamos si el elemento anterior contiene un título en su interior
                Elements subHeaders = prev.select("h1, h2, h3, h4, h5, h6, .post-title");
                if (!subHeaders.isEmpty()) {
                    double num = extraerNumeroContexto(subHeaders.last().text());
                    if (num > 0) return num;
                }
                prev = prev.previousElementSibling();
            }
            // Si no se encontró en los hermanos anteriores, subimos al padre y repetimos
            current = current.parent();
        }
        return 0.0;
    }

    private double extraerNumeroContexto(String texto) {
        // FIX 3: Soporta "Capítulo 2", "Cap. 2" y "Cap 2"
        Matcher m = Pattern.compile("(?i)(?:cap[íi]tulo|cap\\.?)\\s*(\\d+)").matcher(texto);
        if (m.find()) return Double.parseDouble(m.group(1));
        return 0.0;
    }

    private double extraerNumero(String texto) {
        if (texto == null || texto.isEmpty()) return -1;

        // Detectar "Parte X" para que no devuelva error y se le asigne 99.0 internamente
        if (texto.toLowerCase().contains("parte x") || texto.toLowerCase().contains("extra")) {
            return 99.0;
        }

        Pattern patternPartes = Pattern.compile("(?i)(?:Cap[íi]tulo|Ch\\.)\\s*(\\d+).*?(?:parte|p|p\\.)\\s*(\\d+)");
        Matcher matcherPartes = patternPartes.matcher(texto);
        if (matcherPartes.find()) {
            return Double.parseDouble(matcherPartes.group(1)) + (Double.parseDouble(matcherPartes.group(2)) / 100.0);
        }

        Pattern patternEstandar = Pattern.compile("(?i)(?:Cap[íi]tulo|Ch\\.|Vol\\.|Episodio)\\s*(\\d+)");
        Matcher matcherEstandar = patternEstandar.matcher(texto);
        if (matcherEstandar.find()) return Double.parseDouble(matcherEstandar.group(1));

        Pattern patternSiglas = Pattern.compile("^[A-Za-z]{2,5}\\s+(\\d+)");
        Matcher matcherSiglas = patternSiglas.matcher(texto);
        if (matcherSiglas.find()) return Double.parseDouble(matcherSiglas.group(1));

        Pattern patternDecimal = Pattern.compile("(\\d+\\.\\d+)");
        Matcher matcherDecimal = patternDecimal.matcher(texto);
        if (matcherDecimal.find()) return Double.parseDouble(matcherDecimal.group(1));

        Pattern patternGenerico = Pattern.compile("(\\d+)");
        Matcher matcherGenerico = patternGenerico.matcher(texto);
        if (matcherGenerico.find()) return Double.parseDouble(matcherGenerico.group(1));

        return -1;
    }

    public String obtenerSiguientePagina(String html, String urlBase) {
        if (html == null || urlBase == null) return null;
        try {
            SitioWebConfig config = GestorSitios.obtenerConfig(urlBase);
            if (config == null || config.getSelectorSiguientePaginaIndice() == null) {
                return null;
            }
            Document doc = Jsoup.parse(html, urlBase);
            String selectorNext = config.getSelectorSiguientePaginaIndice();
            Element enlaceNext = doc.selectFirst(selectorNext);
            if (enlaceNext != null) {
                String siguienteUrl = enlaceNext.absUrl("href");
                if (siguienteUrl != null && !siguienteUrl.equals(urlBase)) {
                    return siguienteUrl;
                }
            }
        } catch (Exception e) {}
        return null;
    }

    private double extraerNumeroDesdeUrl(String url) {
        if (url == null || url.isEmpty()) return -1;
        Pattern patternCapitulo = Pattern.compile("(?i)(?:cap[ií]tulo|cap)[-_\\s]*?(\\d+)");
        Matcher matcher = patternCapitulo.matcher(url);
        if (matcher.find()) return Double.parseDouble(matcher.group(1));
        
        Pattern patternNum = Pattern.compile("(\\d+)");
        Matcher matcherNum = patternNum.matcher(url);
        if (matcherNum.find()) return Double.parseDouble(matcherNum.group(1));
        return -1;
    }
}