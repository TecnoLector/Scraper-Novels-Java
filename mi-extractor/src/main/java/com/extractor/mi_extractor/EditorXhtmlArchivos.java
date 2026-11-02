package com.extractor.mi_extractor;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/**
 * Operaciones sobre los archivos .xhtml de la carpeta de trabajo: estandarizar y validar
 * títulos, renombrar, limpiar encabezados, vincular CSS, generar el TOC, reparar errores
 * comunes y analizar capítulos faltantes. Extraído de {@link EditorXHTML} sin cambios de
 * comportamiento. Trabaja sobre un snapshot de la lista de .xhtml; no toca el EPUB cargado.
 */
class EditorXhtmlArchivos {

    private final Scanner scanner;
    private final String rutaCarpeta;
    private final File[] archivosXHTML;

    EditorXhtmlArchivos(Scanner scanner, String rutaCarpeta, File[] archivosXHTML) {
        this.scanner = scanner;
        this.rutaCarpeta = rutaCarpeta;
        this.archivosXHTML = archivosXHTML;
    }

    void renombrarArchivosXHTML() {
        System.out.println("Renombrando archivos a formato 'CapituloXXXX.xhtml'...");
        int renombrados = 0;
        for (int i = 0; i < archivosXHTML.length; i++) {
            File archivoActual = archivosXHTML[i];
            String nuevoNombre = String.format("Capitulo%04d.xhtml", i + 1);
            File archivoNuevo = new File(archivoActual.getParent(), nuevoNombre);
            if (archivoActual.renameTo(archivoNuevo)) {
                System.out.println(archivoActual.getName() + " -> " + nuevoNombre);
                renombrados++;
            }
        }
        System.out.println(renombrados + " archivos renombrados.");
    }

    void estandarizarTitulosXHTML() {
        String[] selectoresCandidatos = {
                "h1", "h2", "h3", "h4", "p > strong", "p", "b"
        };
        Map<File, PropuestaCambio> cambiosPropuestos = new LinkedHashMap<>();

        System.out.println("Analizando títulos (basado en nombres de archivo para mayor precisión)...");
        
        // Patrón para extraer el número base y la sub-parte del nombre del archivo (ej. Capitulo0003_01.xhtml)
        Pattern patronArchivo = Pattern.compile("Capitulo(\\d+)(?:_(\\d+))?\\.xhtml", Pattern.CASE_INSENSITIVE);
        // Patrón para limpiar "Capítulo 3:" o números sueltos al inicio del título original
        Pattern patronLimpiar = Pattern.compile("^(?:Cap[ií]tulo\\s+\\d+|\\d+(?![a-z]))[\\s:–—.-]*\\s*(.*)", Pattern.CASE_INSENSITIVE);

        for (File archivo : archivosXHTML) {
            try {
                String contenido = Files.readString(archivo.toPath(), StandardCharsets.UTF_8);
                Document doc = Jsoup.parse(contenido);
                Element tituloElement = null;

                for (String selector : selectoresCandidatos) {
                    tituloElement = doc.selectFirst(selector);
                    if (tituloElement != null) {
                        break;
                    }
                }

                if (tituloElement == null) {
                    System.err.println("Advertencia: No se encontró etiqueta de título en " + archivo.getName());
                    continue;
                }

                String tituloOriginal = tituloElement.text().trim().replace("\uFFFD", "’");
                
                // 1. Extraer el número y parte directamente del nombre del archivo
                Matcher mArch = patronArchivo.matcher(archivo.getName());
                if (mArch.matches()) {
                    int capNum = Integer.parseInt(mArch.group(1));
                    String prefijo = "Capítulo " + capNum;
                    
                    // 2. Si el archivo tiene decimales (ej. _01), formateamos agregando "- Parte X"
                    if (mArch.group(2) != null) {
                        int subNum = Integer.parseInt(mArch.group(2));
                        prefijo += " - Parte " + subNum;
                    }

                    // 3. Limpiamos basuras del título original
                    String nombreLimpio = tituloOriginal;
                    Matcher mLimpiar = patronLimpiar.matcher(nombreLimpio);
                    if (mLimpiar.matches()) {
                        nombreLimpio = mLimpiar.group(1).trim();
                    }
                    if (nombreLimpio.isEmpty()) {
                        nombreLimpio = "(Sin Título)";
                    }

                    // 4. Título final exacto para coincidir <h1> y <title> (Estandarización)
                    String tituloNuevo = prefijo + ": " + nombreLimpio;

                    boolean debePromover = !tituloElement.tagName().equals("h1");
                    if (!tituloOriginal.equals(tituloNuevo) || debePromover) {
                        cambiosPropuestos.put(archivo, new PropuestaCambio(tituloElement.cssSelector(), tituloOriginal, tituloNuevo, debePromover));
                    }
                } else {
                    System.out.println("Info: Archivo no estándar ignorado (" + archivo.getName() + ")");
                }

            } catch (IOException e) {
                System.err.println("Error al leer el archivo: " + archivo.getName());
            }
        }

        // --- FASE 2: REPORTE ---
        if (cambiosPropuestos.isEmpty()) {
            System.out.println("Todos los títulos ya están en el formato estándar <h1>.");
            return;
        }

        System.out.println("\n--- Reporte de Cambios y Promociones de Títulos ---");
        cambiosPropuestos.forEach((file, propuesta) -> {
            System.out.println("Archivo: " + file.getName());
            if (propuesta.promoverTag) {
                System.out.println("  -> Promover (de '" + propuesta.selectorOriginal + "' a h1)");
            }
            System.out.println("  -> Original: \"" + propuesta.tituloOriginal + "\"");
            System.out.println("  -> Nuevo:    \"" + propuesta.tituloNuevo + "\"");
        });

        // --- FASE 3: APLICACIÓN ---
        System.out.print("\n¿Aplicar cambios? (s/n): ");
        if (scanner.nextLine().equalsIgnoreCase("s")) {
            int modificados = 0;
            for (Map.Entry<File, PropuestaCambio> entry : cambiosPropuestos.entrySet()) {
                File archivo = entry.getKey();
                PropuestaCambio propuesta = entry.getValue();
                try {
                    Path ruta = archivo.toPath();
                    String contenido = Files.readString(ruta, StandardCharsets.UTF_8);
                    Document doc = Jsoup.parse(contenido);

                    Element elementoOriginal = doc.selectFirst(propuesta.selectorOriginal);

                    if (elementoOriginal != null) {
                        Element nuevoH1 = doc.createElement("h1").text(propuesta.tituloNuevo);

                        if (propuesta.selectorOriginal.contains("p > strong") &&
                                elementoOriginal.parent() != null &&
                                elementoOriginal.parent().tagName().equals("p")) {
                            elementoOriginal.parent().replaceWith(nuevoH1);
                        } else {
                            elementoOriginal.replaceWith(nuevoH1);
                        }

                        Element titleTag = doc.selectFirst("title");
                        if (titleTag != null) {
                            titleTag.text(propuesta.tituloNuevo);
                        } else {
                            doc.head().append("<title>" + propuesta.tituloNuevo + "</title>");
                        }

                        Files.writeString(ruta, doc.outerHtml(), StandardCharsets.UTF_8);
                        modificados++;
                    } else {
                        System.err.println("Error al aplicar cambio: No se pudo re-encontrar el selector '"
                                + propuesta.selectorOriginal + "' en " + archivo.getName());
                    }
                } catch (IOException e) {
                    System.err.println("Error al modificar: " + archivo.getName() + ": " + e.getMessage());
                } catch (Exception e) {
                    System.err.println("Error inesperado al procesar " + archivo.getName() + ": " + e.getMessage());
                }
            }
            System.out.println(modificados + " archivos modificados.");
        }
    }

    void limpiarEncabezadosXHTML() {
        Pattern patronTituloValido = Pattern.compile("^Cap[ií]tulo\\s+\\d+.*\\w.*", Pattern.CASE_INSENSITIVE);
        String[] etiquetas = { "h1", "h2", "h3", "h4" };
        Map<File, List<String>> problemas = new LinkedHashMap<>();

        for (File archivo : archivosXHTML) {
            try {
                Document doc = Jsoup.parse(archivo, "UTF-8");
                for (String tag : etiquetas) {
                    Elements encabezados = doc.select(tag);
                    Element principal = null;
                    for (Element h : encabezados) {
                        if (h.hasText() && patronTituloValido.matcher(h.text()).find()) {
                            if (principal == null)
                                principal = h;
                            else
                                registrarProblema(problemas, archivo, h);
                        } else {
                            registrarProblema(problemas, archivo, h);
                        }
                    }
                }
            } catch (IOException e) {
                /* ... */ }
        }

        if (problemas.isEmpty()) {
            System.out.println("No se encontraron encabezados basura.");
            return;
        }

        System.out.println("\n--- Reporte de Encabezados Basura ---");
        problemas.forEach((file, list) -> {
            System.out.println("Archivo: " + file.getName());
            list.forEach(item -> System.out.println("  -> " + item));
        });

        System.out.print("\n¿Eliminar encabezados basura? (s/n): ");
        if (scanner.nextLine().equalsIgnoreCase("s")) {
            int modificados = 0;
            for (File archivo : problemas.keySet()) {
                try {
                    Document doc = Jsoup.parse(archivo, "UTF-8");
                    boolean modificado = false;
                    for (String tag : etiquetas) {
                        Elements encabezados = doc.select(tag);
                        Element principal = null;
                        List<Element> paraBorrar = new ArrayList<>();
                        for (Element h : encabezados) {
                            if (h.hasText() && patronTituloValido.matcher(h.text()).find()) {
                                if (principal == null)
                                    principal = h;
                                else
                                    paraBorrar.add(h);
                            } else {
                                paraBorrar.add(h);
                            }
                        }
                        if (!paraBorrar.isEmpty()) {
                            paraBorrar.forEach(Element::remove);
                            modificado = true;
                        }
                    }
                    if (modificado) {
                        Files.writeString(archivo.toPath(), doc.outerHtml(), StandardCharsets.UTF_8);
                        modificados++;
                    }
                } catch (IOException e) {
                    /* ... */ }
            }
            System.out.println(modificados + " archivos modificados.");
        }
    }

    void validarTitulosXHTML() {
        System.out.println("\n¿Qué validación quieres realizar?");
        System.out.println("1. Revisar que el título <h1> siga el formato 'Capítulo # Nombre'.");
        System.out.println("2. Comparar que el título <h1> y la etiqueta <title> sean idénticos.");
        System.out.print("Elige una opción (1 o 2): ");

        int opcion;
        try {
            opcion = scanner.nextInt();
        } catch (java.util.InputMismatchException e) {
            opcion = -1;
        }
        scanner.nextLine();
        File[] archivos = this.archivosXHTML;

        if (archivos == null || archivos.length == 0) {
            System.err.println("No se encontraron archivos .xhtml (error interno en validarTitulosXHTML).");
            return;
        }

        System.out.println("\nSe encontraron " + archivos.length + " capítulos. Empezando la validación...");

        if (opcion == 1) {
            validarFormatoTituloH1(archivos);
        } else if (opcion == 2) {
            validarH1vsTitle(archivos);
        } else {
            System.out.println("Opción no válida.");
        }
    }

    /**
     * Opción 1: Revisa que el
     * <h1>siga el formato "Capítulo # Nombre".
     */
    private static void validarFormatoTituloH1(File[] archivos) {
        Pattern patronTituloValido = Pattern.compile("^Cap[ií]tulo\\s+\\d+.*\\w.*", Pattern.CASE_INSENSITIVE);
        int titulosInvalidos = 0;
        System.out.println("\n--- Reporte de Títulos que NO cumplen el formato 'Capítulo # Nombre' ---");

        for (File archivo : archivos) {
            try {
                String contenido = Files.readString(archivo.toPath(), StandardCharsets.UTF_8);
                Document doc = Jsoup.parse(contenido);
                Element tituloElement = doc.selectFirst("h1");

                if (tituloElement != null && tituloElement.hasText()) {
                    String textoTitulo = tituloElement.text().trim();
                    if (!patronTituloValido.matcher(textoTitulo).find()) {
                        System.out.println("Archivo: " + archivo.getName());
                        System.out.println("  -> Título Inválido o Incompleto: \"" + textoTitulo + "\"");
                        titulosInvalidos++;
                    }
                } else {
                    System.out.println(
                            "Archivo: " + archivo.getName() + " -> Error: No se encontró la etiqueta <h1> del título.");
                    titulosInvalidos++;
                }
            } catch (IOException e) {
                System.err.println("Error al procesar el archivo: " + archivo.getName() + " - " + e.getMessage());
            }
        }

        if (titulosInvalidos == 0) {
            System.out.println("\n¡Perfecto! Todos los títulos tienen el formato correcto.");
        } else {
            System.out.println("\nSe encontraron " + titulosInvalidos
                    + " archivos con títulos que no siguen el formato esperado.");
        }
    }

    /**
     * Opción 2: Compara si el texto de
     * <h1>y <title> son iguales.
     */
    private static void validarH1vsTitle(File[] archivos) {
        int titulosNoCoinciden = 0;
        System.out.println("\n--- Reporte de Títulos que NO coinciden entre <h1> y <title> ---");

        for (File archivo : archivos) {
            try {
                String contenido = Files.readString(archivo.toPath(), StandardCharsets.UTF_8);
                Document doc = Jsoup.parse(contenido);

                Element h1Element = doc.selectFirst("h1");
                Element titleElement = doc.selectFirst("title");

                if (h1Element != null && titleElement != null) {
                    String textoH1 = h1Element.text().trim();
                    String textoTitle = titleElement.text().trim();

                    if (!textoH1.equals(textoTitle)) {
                        System.out.println("Archivo: " + archivo.getName());
                        System.out.println("  -> H1:    \"" + textoH1 + "\"");
                        System.out.println("  -> Title: \"" + textoTitle + "\"");
                        titulosNoCoinciden++;
                    }
                } else {
                    System.out.println(
                            "Archivo: " + archivo.getName() + " -> Error: No se encontró la etiqueta <h1> o <title>.");
                    titulosNoCoinciden++;
                }
            } catch (IOException e) {
                System.err.println("Error al procesar el archivo: " + archivo.getName() + " - " + e.getMessage());
            }
        }

        if (titulosNoCoinciden == 0) {
            System.out.println("\n¡Perfecto! Todos los títulos <h1> y <title> coinciden.");
        } else {
            System.out.println("\nSe encontraron " + titulosNoCoinciden + " archivos donde los títulos no coinciden.");
        }
    }

    void vincularCssXHTML() {
        System.out.println("Introduce el nombre del archivo CSS que quieres vincular (ej: style.css):");
        String nombreCss = scanner.nextLine();

        String linkHtml = "<link rel=\"stylesheet\" type=\"text/css\" href=\"" + nombreCss + "\" />";
        int archivosModificados = 0;

        System.out.println("Vinculando '" + nombreCss + "' a " + archivosXHTML.length + " archivos...");

        for (File archivo : archivosXHTML) {
            try {
                Path ruta = archivo.toPath();
                String contenido = Files.readString(ruta, StandardCharsets.UTF_8);
                Document doc = Jsoup.parse(contenido);

                boolean yaVinculado = false;
                Elements links = doc.head().select("link[href=\"" + nombreCss + "\"]");
                if (links.size() > 0) {
                    yaVinculado = true;
                }

                if (!yaVinculado) {
                    // Si no lo tiene, lo añade al final del <head>
                    doc.head().append(linkHtml);
                    Files.writeString(ruta, doc.outerHtml(), StandardCharsets.UTF_8);
                    archivosModificados++;
                }

            } catch (IOException e) {
                System.err.println("Error al procesar: " + archivo.getName());
            }
        }
        System.out.println(archivosModificados + " archivos fueron actualizados con el vínculo CSS.");
    }

    void generarTocXHTML() {
        System.out.println("Generando 'toc.xhtml'...");
        
        // CORRECCIÓN 1: Ruta correcta dentro de OEBPS/Text
        Path rutaToc = Paths.get(rutaCarpeta, "OEBPS", "Text", "toc.xhtml");
        
        StringBuilder tocBuilder = new StringBuilder();
        tocBuilder.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                .append("<!DOCTYPE html>\n")
                .append("<html xmlns=\"http://www.w3.org/1999/xhtml\" xmlns:epub=\"http://www.idpf.org/2007/ops\" lang=\"es\">\n")
                .append("<head>\n")
                .append("    <meta charset=\"UTF-8\"/>\n")
                .append("    <title>Tabla de Contenidos</title>\n")
                // CORRECCIÓN 2: Ruta relativa correcta para CSS (subir un nivel)
                .append("    <link rel=\"stylesheet\" type=\"text/css\" href=\"../Styles/stylesheet.css\"/>\n")
                .append("</head>\n")
                .append("<body>\n")
                .append("    <nav epub:type=\"toc\" id=\"toc\">\n")
                .append("        <h1>Índice</h1>\n")
                .append("        <ol>\n");

        for (File archivo : archivosXHTML) {
            // Evitar que el TOC se enlace a sí mismo
            if (archivo.getName().equalsIgnoreCase("toc.xhtml")) continue;

            try {
                Document doc = Jsoup.parse(archivo, "UTF-8");
                Element h1 = doc.selectFirst("h1");
                String tituloRaw = (h1 != null) ? h1.text() : archivo.getName().replace(".xhtml", "");
                
                // Limpieza básica de entidades
                String tituloLimpio = tituloRaw.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
                
                // Codificación de URL para espacios y caracteres especiales
                String nombreArchivo = archivo.getName();
                String hrefCodificado = URLEncoder.encode(nombreArchivo, StandardCharsets.UTF_8.toString()).replace("+", "%20").replace("%2E", ".");

                tocBuilder.append("            <li><a href=\"").append(hrefCodificado).append("\">").append(tituloLimpio).append("</a></li>\n");

            } catch (IOException e) {
                System.err.println("Advertencia: No se pudo leer " + archivo.getName());
            }
        }

        tocBuilder.append("        </ol>\n").append("    </nav>\n").append("</body>\n").append("</html>");

        try {
            // Aseguramos que la carpeta existe (debería, porque ahí están los .xhtml)
            Files.createDirectories(rutaToc.getParent());
            Files.writeString(rutaToc, tocBuilder.toString(), StandardCharsets.UTF_8);
            System.out.println("'toc.xhtml' generado correctamente en: " + rutaToc);
        } catch (IOException e) {
            System.err.println("Error grave al guardar 'toc.xhtml': " + e.getMessage());
        }
    }

    private void registrarProblema(Map<File, List<String>> mapa, File archivo, Element elemento) {
        mapa.computeIfAbsent(archivo, k -> new ArrayList<>())
                .add("<" + elemento.tagName() + ">: \"" + elemento.text() + "\"");
    }

    void repararErroresComunes() {
    System.out.println("Buscando errores comunes en " + archivosXHTML.length + " archivos .xhtml...");

    Map<File, List<String>> reporteProblemas = new LinkedHashMap<>();
    Map<File, Map<String, List<Integer>>> ubicacionesErrores = new LinkedHashMap<>();

    // --- FASE 1: DIAGNÓSTICO CON TRACKING DE LÍNEAS ---
    for (File archivo : archivosXHTML) {
        try {
            String contenido = Files.readString(archivo.toPath(), StandardCharsets.UTF_8);
            String[] lineas = contenido.split("\n");
            List<String> erroresEncontrados = new ArrayList<>();
            Map<String, List<Integer>> erroresEnEsteArchivo = new LinkedHashMap<>();
            boolean problemaDetectadoEsteArchivo = false;

            // Buscar cada tipo de error Y registrar en qué línea está
            if (!contenido.trim().startsWith("<?xml")) {
                erroresEncontrados.add("Falta declaración '<?xml ... ?>'");
                erroresEnEsteArchivo.put("Declaración XML", List.of(1));
                problemaDetectadoEsteArchivo = true;
            }
            
            // DOCTYPE
            java.util.regex.Pattern patronDoctype = java.util.regex.Pattern.compile("(?i)<!doctype html>");
            for (int i = 0; i < lineas.length; i++) {
                if (patronDoctype.matcher(lineas[i]).find()) {
                    if (!erroresEncontrados.contains("DOCTYPE en minúsculas")) {
                        erroresEncontrados.add("DOCTYPE en minúsculas (rompe el XML)");
                    }
                    erroresEnEsteArchivo.computeIfAbsent("DOCTYPE", k -> new ArrayList<>()).add(i + 1);
                    problemaDetectadoEsteArchivo = true;
                }
            }

            // Caracteres invisibles
            java.util.regex.Pattern patronInvisibles = java.util.regex.Pattern.compile("[\\u200B-\\u200D\\uFEFF]");
            for (int i = 0; i < lineas.length; i++) {
                if (patronInvisibles.matcher(lineas[i]).find()) {
                    if (!erroresEncontrados.contains("Contiene caracteres invisibles")) {
                        erroresEncontrados.add("Contiene caracteres invisibles (Anti-scraping)");
                    }
                    erroresEnEsteArchivo.computeIfAbsent("Caracteres invisibles", k -> new ArrayList<>()).add(i + 1);
                    problemaDetectadoEsteArchivo = true;
                }
            }

            // Basura web (mgid-widget, _ngcontent, etc.)
            java.util.regex.Pattern patronBasura = java.util.regex.Pattern.compile("mgid-widget|_ngcontent|<aside");
            for (int i = 0; i < lineas.length; i++) {
                if (patronBasura.matcher(lineas[i]).find()) {
                    if (!erroresEncontrados.contains("Contiene basura web")) {
                        erroresEncontrados.add("Contiene basura web (Anuncios o etiquetas de Angular)");
                    }
                    erroresEnEsteArchivo.computeIfAbsent("Basura web", k -> new ArrayList<>()).add(i + 1);
                    problemaDetectadoEsteArchivo = true;
                }
            }

            // &nbsp;
            java.util.regex.Pattern patronNbsp = java.util.regex.Pattern.compile("&nbsp;");
            for (int i = 0; i < lineas.length; i++) {
                if (patronNbsp.matcher(lineas[i]).find()) {
                    if (!erroresEncontrados.contains("Contiene '&nbsp;'")) {
                        erroresEncontrados.add("Contiene '&nbsp;' (inválido en XML estricto)");
                    }
                    erroresEnEsteArchivo.computeIfAbsent("&nbsp;", k -> new ArrayList<>()).add(i + 1);
                    problemaDetectadoEsteArchivo = true;
                }
            }

            // Etiquetas no auto-cerradas
            java.util.regex.Pattern patronImg = java.util.regex.Pattern.compile("(?i)<img(?:\"[^\"]*\"|'[^']*'|[^'\">])*?(?<!/)\\s*>");
            java.util.regex.Pattern patronBr = java.util.regex.Pattern.compile("(?i)<br\\s*(?!/)[^>]*>");
            java.util.regex.Pattern patronLink = java.util.regex.Pattern.compile("(?i)<link[^>]+(?<!/)>");
            java.util.regex.Pattern patronHr = java.util.regex.Pattern.compile("(?i)<hr[^>]*/?>");
            
            for (int i = 0; i < lineas.length; i++) {
                if (patronImg.matcher(lineas[i]).find()) {
                    erroresEnEsteArchivo.computeIfAbsent("img", k -> new ArrayList<>()).add(i + 1);
                    problemaDetectadoEsteArchivo = true;
                }
                if (patronBr.matcher(lineas[i]).find()) {
                    erroresEnEsteArchivo.computeIfAbsent("br", k -> new ArrayList<>()).add(i + 1);
                    problemaDetectadoEsteArchivo = true;
                }
                if (patronLink.matcher(lineas[i]).find()) {
                    erroresEnEsteArchivo.computeIfAbsent("link", k -> new ArrayList<>()).add(i + 1);
                    problemaDetectadoEsteArchivo = true;
                }
                if (patronHr.matcher(lineas[i]).find()) {
                    erroresEnEsteArchivo.computeIfAbsent("hr", k -> new ArrayList<>()).add(i + 1);
                    problemaDetectadoEsteArchivo = true;
                }
            }

            // Markdown y o:p
            java.util.regex.Pattern patronMarkdown = java.util.regex.Pattern.compile("(?i)<markdown|</?o:p");
            for (int i = 0; i < lineas.length; i++) {
                if (patronMarkdown.matcher(lineas[i]).find()) {
                    if (lineas[i].toLowerCase().contains("markdown")) {
                        erroresEnEsteArchivo.computeIfAbsent("markdown", k -> new ArrayList<>()).add(i + 1);
                    }
                    if (lineas[i].toLowerCase().contains("o:p")) {
                        erroresEnEsteArchivo.computeIfAbsent("o:p", k -> new ArrayList<>()).add(i + 1);
                    }
                    problemaDetectadoEsteArchivo = true;
                }
            }

            // Registrar problemas
            if (problemaDetectadoEsteArchivo) {
                reporteProblemas.put(archivo, erroresEncontrados);
                ubicacionesErrores.put(archivo, erroresEnEsteArchivo);
            }

        } catch (IOException e) {
            System.err.println("Error CRÍTICO al leer el archivo: " + archivo.getName() + " - " + e.getMessage());
            reporteProblemas.put(archivo, List.of("Error de lectura del archivo"));
        }
    }

    // --- FASE 2: REPORTE Y CONFIRMACIÓN ---
    if (reporteProblemas.isEmpty()) {
        System.out.println("\n¡Perfecto! No se encontraron archivos que necesitaran reparación.");
        return;
    } 
    
    System.out.println("\n--- Reporte Detallado de Errores de Formato ---");
    reporteProblemas.forEach((archivo, listaErrores) -> {
        System.out.println(" -> " + archivo.getName() + ":");
        listaErrores.forEach(error -> System.out.println("    - " + error));
    });

    System.out.println("\nSe encontraron " + reporteProblemas.size() + " archivos con problemas.");
    System.out.print("¿Quieres intentar repararlos todos ahora? (s/n): ");
    String respuesta = this.scanner.nextLine();

    // --- FASE 3: REPARACIÓN AVANZADA CON JSOUP ---
    if (respuesta.equalsIgnoreCase("s")) {
        int archivosReparados = 0;
        int archivosFallidos = 0;
        Map<String, Integer> conteoReparaciones = new LinkedHashMap<>();  // Contar reparaciones por tipo
        Map<String, Map<String, List<Integer>>> resumenReparaciones = new LinkedHashMap<>();  // errorType -> archivo -> líneas
        
        System.out.println("\nEmpezando la reparación...");
        
        for (File archivo : reporteProblemas.keySet()) {
            if (reporteProblemas.get(archivo).contains("Error de lectura del archivo")) {
                System.out.println(" -> ERROR: No se pudo reparar " + archivo.getName() + " debido a un error de lectura.");
                archivosFallidos++;
                continue;
            }

            try {
                Path rutaArchivo = archivo.toPath();
                String contenido = Files.readString(rutaArchivo, StandardCharsets.UTF_8);
                Map<String, List<Integer>> erroresEsteArchivo = ubicacionesErrores.get(archivo);

                // Registrar qué se va a reparar en este archivo
                if (erroresEsteArchivo != null) {
                    for (String tipoError : erroresEsteArchivo.keySet()) {
                        resumenReparaciones.computeIfAbsent(tipoError, k -> new LinkedHashMap<>())
                                          .put(archivo.getName(), erroresEsteArchivo.get(tipoError));
                        conteoReparaciones.put(tipoError, conteoReparaciones.getOrDefault(tipoError, 0) + 1);
                    }
                }

                // 1. Limpieza PRE-JSoup
                contenido = contenido.replaceAll("[\\u200B-\\u200D\\uFEFF]", "");
                contenido = contenido.replaceAll("(?i)<!doctype html>", "");
                contenido = contenido.replaceAll("<!--\\?xml.*?\\?-->", "");
                contenido = contenido.replace("&nbsp;", "&#160;");

                // 2. Parseo estricto XML con JSoup
                org.jsoup.nodes.Document doc = org.jsoup.Jsoup.parse(contenido, "", org.jsoup.parser.Parser.xmlParser());
                doc.outputSettings()
                   .syntax(org.jsoup.nodes.Document.OutputSettings.Syntax.xml)
                   .escapeMode(org.jsoup.nodes.Entities.EscapeMode.xhtml)
                   .prettyPrint(false);

                // 3. Eliminar basura estructural
                doc.select("aside, mgid-widget, script, style, iframe, markdown, o\\:p, hr").remove();

                // 4. Limpiar atributos basura (mantener aria-*)
                for (org.jsoup.nodes.Element el : doc.getAllElements()) {
                    List<String> keysToRemove = new ArrayList<>();
                    for (org.jsoup.nodes.Attribute attr : el.attributes()) {
                        if (attr.getKey().startsWith("_ng") || 
                            (attr.getKey().startsWith("data-") && !attr.getKey().equals("data-id"))) {
                            keysToRemove.add(attr.getKey());
                        }
                    }
                    for (String key : keysToRemove) {
                        el.removeAttr(key);
                    }
                }

                // 5. Generar documento final
                String contenidoFinal = doc.outerHtml();
                contenidoFinal = contenidoFinal.replaceAll("(?i)<!DOCTYPE[^>]*>\\s*", "");
                
                if (contenidoFinal.startsWith("<?xml")) {
                    contenidoFinal = contenidoFinal.replaceFirst("<\\?xml.*?\\?>\\s*", "");
                }
                contenidoFinal = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" + 
                                 "<!DOCTYPE html>\n" + 
                                 contenidoFinal.trim();

                // 6. VALIDACIÓN FINAL
                boolean validacionExitosa = false;
                try {
                    org.jsoup.Jsoup.parse(contenidoFinal, "", org.jsoup.parser.Parser.xmlParser());
                    validacionExitosa = true;
                } catch (Exception e) {
                    System.out.println(" ⚠ Advertencia: El archivo " + archivo.getName() + 
                                     " puede tener errores XML residuales. Intenta abrir en Sigil para verificar.");
                }

                // 7. Guardar el archivo reparado
                Files.writeString(rutaArchivo, contenidoFinal, StandardCharsets.UTF_8);
                
                if (validacionExitosa) {
                    System.out.println(" ✓ Archivo reparado con éxito: " + archivo.getName());
                } else {
                    System.out.println(" ⚠ Archivo procesado pero con posibles advertencias: " + archivo.getName());
                }
                archivosReparados++;

            } catch (Exception e) {
                System.err.println(" ✗ Error al intentar reparar el archivo: " + archivo.getName() + 
                                 " - " + e.getMessage());
                archivosFallidos++;
            }
        }
        
        // --- RESUMEN FINAL GRANULAR ---
        System.out.println("\n" + "=".repeat(70));
        System.out.println("RESUMEN DETALLADO DE REPARACIONES POR TIPO DE ERROR:");
        System.out.println("=".repeat(70));
        
        if (resumenReparaciones.isEmpty()) {
            System.out.println("No se realizó ninguna reparación.");
        } else {
            for (String tipoError : resumenReparaciones.keySet()) {
                Map<String, List<Integer>> detallesError = resumenReparaciones.get(tipoError);
                int totalOcurrencias = detallesError.values().stream().mapToInt(List::size).sum();
                int totalCapitulos = detallesError.size();
                
                System.out.println("\n✅ " + tipoError.toUpperCase() + " - Reparado en " + totalCapitulos + " capítulo(s) (" + totalOcurrencias + " ocurrencias):");
                
                for (String nombreArchivo : detallesError.keySet()) {
                    int ocurrenciasEnArchivo = detallesError.get(nombreArchivo).size();
                    System.out.println("   • " + nombreArchivo + " (" + ocurrenciasEnArchivo + ")");
                }
            }
        }
        
        System.out.println("\n" + "=".repeat(70));
        System.out.println("RESUMEN EJECUTIVO:");
        System.out.println(" ✓ Archivos reparados exitosamente: " + archivosReparados);
        System.out.println(" ✗ Archivos con errores: " + archivosFallidos);
        System.out.println(" 📊 Total de errores reparados: " + 
                          resumenReparaciones.values().stream()
                                             .flatMap(m -> m.values().stream())
                                             .mapToInt(List::size).sum());
        System.out.println("=".repeat(70));
        
    } else {
        System.out.println("\nNo se realizó ninguna modificación.");
    }
}

    // --- MÉTODOS PARA EPUB ---


    void analizarCapitulosFaltantes() {
        System.out.println("\n--- Análisis de Integridad de Capítulos ---");
        
        List<Integer> encontrados = new ArrayList<>();
        // Busca números en los nombres de archivo (ej: "Capitulo0023.xhtml" -> 23)
        Pattern patronNumero = Pattern.compile("(\\d+)"); 

        for (File archivo : archivosXHTML) {
            if (archivo.getName().toLowerCase().contains("toc") || archivo.getName().toLowerCase().contains("info")) {
                continue; // Ignorar índice y metadatos
            }
            
            Matcher m = patronNumero.matcher(archivo.getName());
            if (m.find()) {
                try {
                    encontrados.add(Integer.parseInt(m.group(1)));
                } catch (Exception e) {} 
            }
        }

        Collections.sort(encontrados);

        if (encontrados.isEmpty()) {
            System.out.println("-> No se detectaron números en los nombres de archivo.");
            System.out.println("Recomendación: Usa la opción '2. Renombrar Archivos' primero.");
            return;
        }

        int max = encontrados.get(encontrados.size() - 1);
        int total = encontrados.size();

        System.out.println("Resumen:");
        System.out.println(" -> Archivos numéricos encontrados: " + total);
        System.out.println(" -> Último capítulo detectado: " + max);

        List<Integer> faltantes = new ArrayList<>();
        for (int i = 1; i <= max; i++) {
            if (!encontrados.contains(i)) {
                faltantes.add(i);
            }
        }

        if (faltantes.isEmpty()) {
            System.out.println("\n ¡SECUENCIA COMPLETA! Tienes todos los capítulos del 1 al " + max);
        } else {
            System.out.println("\n SE DETECTARON " + faltantes.size() + " HUECOS.");
            System.out.println("Lista de capítulos faltantes (para copiar en el Extractor):");
            System.out.println("--------------------------------------------------");
            // Genera la lista separada por comas: "1,4,5,10"
            String listaCopiable = faltantes.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(", "));
            System.out.println(listaCopiable);
            System.out.println("--------------------------------------------------");
        }
    }

    private static class PropuestaCambio {
        String selectorOriginal;
        String tituloOriginal;
        String tituloNuevo;
        boolean promoverTag;

        PropuestaCambio(String selectorOriginal, String tituloOriginal, String tituloNuevo, boolean promoverTag) {
            this.selectorOriginal = selectorOriginal;
            this.tituloOriginal = tituloOriginal;
            this.tituloNuevo = tituloNuevo;
            this.promoverTag = promoverTag;
        }
    }
}
