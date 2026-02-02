package com.extractor.mi_extractor;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import nl.siegmann.epublib.domain.Book;
import nl.siegmann.epublib.domain.Resource;
import nl.siegmann.epublib.epub.EpubWriter;
import nl.siegmann.epublib.domain.MediaType;
import nl.siegmann.epublib.service.MediatypeService;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import java.io.BufferedInputStream;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import java.util.stream.Stream;

public class HerramientasEpub {

    private final Scanner scanner;
    private final String rutaCarpeta;
    private File[] archivos; // Modificable para que se actualice después de renombrar

    public HerramientasEpub(Scanner scanner) {
        this.scanner = scanner;
        System.out.println("Introduce la ruta de la carpeta donde están tus archivos .xhtml:");
        this.rutaCarpeta = scanner.nextLine();
        actualizarListaDeArchivos();
    }

    private void actualizarListaDeArchivos() {
        File carpeta = new File(this.rutaCarpeta);
        this.archivos = carpeta.listFiles((dir, name) -> name.toLowerCase().endsWith(".xhtml"));
        if (this.archivos != null) {
            Arrays.sort(this.archivos, Comparator.comparing(File::getName));
        }
    }

    public void mostrarMenu() {
        actualizarListaDeArchivos();

        while (true) {
            boolean hayArchivos = (archivos != null && archivos.length > 0);

            System.out.println("\n--- Herramientas de Post-Procesado ---");

            if (hayArchivos) {
                System.out.println("1. Estandarizar Títulos (formato 'Capítulo #: Nombre')");
                System.out.println("2. Renombrar Archivos (formato 'Capitulo0001.xhtml')");
                System.out.println("3. Limpiar Encabezados Basura (h1, h2, etc.)");
                System.out.println("4. Validar Títulos (...)");
                System.out.println("5. Vincular Hoja de Estilo (CSS) a todos los capítulos");
                System.out.println("6. Generar Archivo de Índice (toc.xhtml)");
            } else {
                System.out.println("[No se han detectado archivos .xhtml en la carpeta]");
                System.out.println("[Las herramientas 1-6 están desactivadas]");
            }

            System.out.println("7. Crear Archivo EPUB (desde .xhtml)");
            System.out.println("8. Dividir un EPUB por rango de capítulos");
            System.out.println("9. Descomprimir un EPUB (Extraer archivos)");
            System.out.println("10. Re-empaquetar carpeta como EPUB");
            System.out.println("0. Volver al menú principal");
            System.out.print("Elige una herramienta: ");

            int opcion = scanner.nextInt();
            scanner.nextLine();

            switch (opcion) {
                case 1:
                    if (!hayArchivos)
                        break;
                    estandarizarTitulos();
                    break;
                case 2:
                    if (!hayArchivos)
                        break;
                    renombrarArchivos();
                    break;
                case 3:
                    if (!hayArchivos)
                        break;
                    limpiarEncabezados();
                    break;
                case 4:
                    if (!hayArchivos)
                        break;
                    validarTitulos();
                    break;
                case 5:
                    if (!hayArchivos)
                        break;
                    vincularCss();
                    break;
                case 6:
                    if (!hayArchivos)
                        break;
                    generarToc();
                    break;
                case 7:
                    if (!hayArchivos) {
                        System.err.println("Error: No hay archivos .xhtml para crear un EPUB.");
                        break;
                    }
                    crearEpub();
                    break;
                case 8:
                    dividirEpub();
                    break;
                case 9:
                    descomprimirEpub();
                    break;
                case 10:
                    reempaquetarComoEpub();
                    break;
                case 0:
                    return;
                default:
                    System.out.println("Opción no válida.");
            }
        }
    }

    private void estandarizarTitulos() {
        Pattern patronExtraccion = Pattern.compile(".*Cap[ií]tulo\\s*(\\d+)[\\s:–—.-]?\\s*(.*)",
                Pattern.CASE_INSENSITIVE);
        Map<File, String[]> cambiosPropuestos = new LinkedHashMap<>();

        System.out.println("Analizando títulos...");
        for (File archivo : archivos) {
            try {
                String contenido = Files.readString(archivo.toPath(), StandardCharsets.UTF_8);
                Document doc = Jsoup.parse(contenido);
                Element h1 = doc.selectFirst("h1");

                if (h1 != null && h1.hasText()) {
                    String tituloOriginal = h1.text().trim();
                    String tituloSanitizado = tituloOriginal.replace("\uFFFD", "’");
                    Matcher matcher = patronExtraccion.matcher(tituloSanitizado);

                    if (matcher.find()) {
                        String numero = matcher.group(1);
                        String nombre = matcher.group(2).trim();
                        if (nombre.isEmpty())
                            nombre = "(Título no encontrado)";
                        String tituloNuevo = "Capítulo " + numero + ": " + nombre;

                        if (!tituloOriginal.equals(tituloNuevo)) {
                            cambiosPropuestos.put(archivo, new String[] { tituloOriginal, tituloNuevo });
                        }
                    }
                }
            } catch (IOException e) {
                System.err.println("Error al leer el archivo: " + archivo.getName());
            }
        }

        if (cambiosPropuestos.isEmpty()) {
            System.out.println("Todos los títulos ya están en el formato estándar.");
            return;
        }

        System.out.println("\n--- Reporte de Cambios Propuestos ---");
        cambiosPropuestos.forEach((file, titles) -> {
            System.out.println("Archivo: " + file.getName());
            System.out.println("  -> Original: \"" + titles[0] + "\"");
            System.out.println("  -> Nuevo:    \"" + titles[1] + "\"");
        });

        System.out.print("\n¿Aplicar cambios? (s/n): ");
        if (scanner.nextLine().equalsIgnoreCase("s")) {
            int modificados = 0;
            for (Map.Entry<File, String[]> entry : cambiosPropuestos.entrySet()) {
                try {
                    Path ruta = entry.getKey().toPath();
                    String contenido = Files.readString(ruta, StandardCharsets.UTF_8);
                    Document doc = Jsoup.parse(contenido);
                    doc.selectFirst("h1").text(entry.getValue()[1]);
                    doc.selectFirst("title").text(entry.getValue()[1]);
                    Files.writeString(ruta, doc.outerHtml(), StandardCharsets.UTF_8);
                    modificados++;
                } catch (IOException e) {
                    System.err.println("Error al modificar: " + entry.getKey().getName());
                }
            }
            System.out.println(modificados + " archivos modificados.");
        }
    }

    private void renombrarArchivos() {
        System.out.println("Renombrando archivos a formato 'CapituloXXXX.xhtml'...");
        int renombrados = 0;
        for (int i = 0; i < archivos.length; i++) {
            File archivoActual = archivos[i];
            String nuevoNombre = String.format("Capitulo%04d.xhtml", i + 1);
            File archivoNuevo = new File(archivoActual.getParent(), nuevoNombre);
            if (archivoActual.renameTo(archivoNuevo)) {
                System.out.println(archivoActual.getName() + " -> " + nuevoNombre);
                renombrados++;
            }
        }
        System.out.println(renombrados + " archivos renombrados.");
        actualizarListaDeArchivos();
    }

    private void limpiarEncabezados() {
        Pattern patronTituloValido = Pattern.compile("^Cap[ií]tulo\\s+\\d+.*\\w.*", Pattern.CASE_INSENSITIVE);
        String[] etiquetas = { "h1", "h2", "h3", "h4" };
        Map<File, List<String>> problemas = new LinkedHashMap<>();

        for (File archivo : archivos) {
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

    private void validarTitulos() {
        System.out.println("\n¿Qué validación quieres realizar?");
        System.out.println("1. Revisar que el título <h1> siga el formato 'Capítulo # Nombre'.");
        System.out.println("2. Comparar que el título <h1> y la etiqueta <title> sean idénticos.");
        System.out.print("Elige una opción (1 o 2): ");
        int opcion = scanner.nextInt();
        // --------------------------------

        File carpeta = new File(rutaCarpeta);

        if (!carpeta.exists() || !carpeta.isDirectory()) {
            System.err.println("Error: La ruta proporcionada no es una carpeta válida.");
            return;
        }

        File[] archivos = carpeta.listFiles((dir, name) -> name.toLowerCase().endsWith(".xhtml"));

        if (archivos == null || archivos.length == 0) {
            System.err.println("No se encontraron archivos .xhtml en la carpeta.");
            return;
        }

        Arrays.sort(archivos, Comparator.comparing(File::getName));
        System.out.println("\nSe encontraron " + archivos.length + " capítulos. Empezando la validación...");

        // Ejecutar la validación elegida
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

    private void vincularCss() {
        System.out.println("Introduce el nombre del archivo CSS que quieres vincular (ej: style.css):");
        String nombreCss = scanner.nextLine();

        String linkHtml = "<link rel=\"stylesheet\" type=\"text/css\" href=\"" + nombreCss + "\" />";
        int archivosModificados = 0;

        System.out.println("Vinculando '" + nombreCss + "' a " + archivos.length + " archivos...");

        for (File archivo : archivos) {
            try {
                Path ruta = archivo.toPath();
                String contenido = Files.readString(ruta, StandardCharsets.UTF_8);
                Document doc = Jsoup.parse(contenido);

                // Revisar si ya tiene ese link para no duplicar
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

    private void generarToc() {
        System.out.println("Generando 'toc.xhtml'...");
        StringBuilder tocBuilder = new StringBuilder(
                "<!DOCTYPE html><html><head><title>TOC</title></head><body><h1>Índice</h1><nav epub:type=\"toc\"><ol>\n");
        for (File archivo : archivos) {
            try {
                Document doc = Jsoup.parse(archivo, "UTF-8");
                Element h1 = doc.selectFirst("h1");
                String titulo = (h1 != null) ? h1.text() : archivo.getName();
                tocBuilder.append("<li><a href=\"").append(archivo.getName()).append("\">").append(titulo)
                        .append("</a></li>\n");
            } catch (IOException e) {
                /* ... */ }
        }
        tocBuilder.append("</ol></nav></body></html>");
        try {
            Files.writeString(Paths.get(rutaCarpeta, "toc.xhtml"), tocBuilder.toString());
            System.out.println("'toc.xhtml' generado con éxito.");
        } catch (IOException e) {
            System.err.println("Error al guardar 'toc.xhtml'.");
        }
    }

    private void crearEpub() {
        System.out.println("Introduce el título del libro:");
        String tituloLibro = scanner.nextLine();
        System.out.println("Introduce el autor:");
        String autorLibro = scanner.nextLine();
        System.out.println("Introduce la sinopsis (deja en blanco si no hay):");
        String sinopsis = scanner.nextLine();
        System.out.println("Introduce los géneros (separados por coma, ej: Fantasía, Aventura):");
        String generosStr = scanner.nextLine();
        System.out.println("Introduce el nombre del archivo CSS (ej: style.css, el mismo que usaste para vincular):");
        String nombreCss = scanner.nextLine();
        Path rutaCss = (nombreCss.isEmpty()) ? null : Paths.get(rutaCarpeta, nombreCss);
        System.out.println("Introduce el nombre de la imagen de portada (ej: portada.jpg, deja en blanco si no hay):");
        String nombrePortada = scanner.nextLine();
        Path rutaPortada = (nombrePortada.isEmpty()) ? null : Paths.get(rutaCarpeta, nombrePortada);
        System.out.println("Introduce el nombre del archivo de salida (ej: MiLibro.epub):");
        String archivoSalida = scanner.nextLine();

        try {
            Book libro = new Book();

            // --- 1. AÑADIR METADATOS ---
            libro.getMetadata().addTitle(tituloLibro);
            libro.getMetadata().addAuthor(new nl.siegmann.epublib.domain.Author(autorLibro));
            if (sinopsis != null && !sinopsis.isEmpty()) {
                libro.getMetadata().addDescription(sinopsis);
            }
            if (generosStr != null && !generosStr.isEmpty()) {
                java.util.List<String> listaDeGeneros = new java.util.ArrayList<>();
                for (String genero : generosStr.split(",")) {
                    listaDeGeneros.add(genero.trim());
                }
                libro.getMetadata().setSubjects(listaDeGeneros);
            }

            // --- 2. AÑADIR HOJA DE ESTILO CSS ---
            if (rutaCss != null && Files.exists(rutaCss)) {
                Resource cssResource = new Resource(new FileInputStream(rutaCss.toFile()), nombreCss);
                cssResource.setMediaType(MediatypeService.CSS); // Especificamos que es CSS
                libro.getResources().add(cssResource);
                System.out.println("Archivo CSS '" + nombreCss + "' empaquetado.");
            }

            // --- 3. AÑADIR IMAGEN DE PORTADA ---
            if (rutaPortada != null && Files.exists(rutaPortada)) {
                Resource coverResource = new Resource(new FileInputStream(rutaPortada.toFile()),
                        "portada_epub." + (nombrePortada.endsWith("png") ? "png" : "jpg"));

                // Especificamos el tipo de imagen
                if (nombrePortada.toLowerCase().endsWith(".png")) {
                    coverResource.setMediaType(MediatypeService.PNG);
                } else {
                    coverResource.setMediaType(MediatypeService.JPG);
                }

                libro.setCoverImage(coverResource);
                System.out.println("Imagen de portada añadida.");
            }

            // --- 4. AÑADIR PÁGINA DE SINOPSIS (Vinculando el CSS) ---
            if (sinopsis != null && !sinopsis.isEmpty()) {
                String sinopsisHtml = "<?xml version='1.0' encoding='utf-8'?>\n"
                        + "<html xmlns=\"http://www.w3.org/1999/xhtml\">\n"
                        + "<head><title>Sinopsis</title>\n"
                        + ((rutaCss != null)
                                ? "<link rel=\"stylesheet\" type=\"text/css\" href=\"" + nombreCss + "\" />\n"
                                : "") // Link al CSS
                        + "</head>\n"
                        + "<body>\n"
                        + " <h1>Sinopsis</h1>\n"
                        + " <p>" + sinopsis.replace("\n", "<br/>") + "</p>\n"
                        + " <h2>Géneros</h2>\n"
                        + " <p>" + generosStr + "</p>\n"
                        + "</body>\n"
                        + "</html>";

                Resource sinopsisResource = new Resource(sinopsisHtml.getBytes(StandardCharsets.UTF_8),
                        "sinopsis.xhtml");
                libro.addSection("Sinopsis", sinopsisResource);
            }

            // --- 5. AÑADIR TODOS LOS CAPÍTULOS ---
            for (File archivo : archivos) {
                libro.addSection(
                        archivo.getName().replace(".xhtml", ""),
                        new Resource(new FileInputStream(archivo), archivo.getName()));
            }

            // --- 6. ESCRIBIR EL EPUB ---
            new EpubWriter().write(libro, new FileOutputStream(new File(rutaCarpeta, archivoSalida)));
            System.out.println("EPUB (versión completa) creado con éxito: " + archivoSalida);

        } catch (IOException e) {
            System.err.println("Error al crear el EPUB: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void dividirEpub() {
        System.out.println("Introduce el nombre del archivo EPUB a dividir (ej: MiLibro.epub):");
        String nombreEpubOriginal = scanner.nextLine();
        Path rutaEpubOriginal = Paths.get(rutaCarpeta, nombreEpubOriginal);

        if (!Files.exists(rutaEpubOriginal)) {
            System.err.println("Error: El archivo " + nombreEpubOriginal + " no se encuentra en la carpeta.");
            return;
        }

        System.out.print("¿Desde que capítulo quieres empezar? (numero): ");
        int inicio = scanner.nextInt();
        System.out.print("¿Hasta que capítulo quieres terminar? (numero): ");
        int fin = scanner.nextInt();
        scanner.nextLine();

        System.out.println("Introduce el nombre del nuevo archivo EPUB (ej: MiLibro_parte1.epub):");
        String nombreEpubNuevo = scanner.nextLine();
        Path rutaEpubNuevo = Paths.get(rutaCarpeta, nombreEpubNuevo);

        try {
            // 1. Cargar el libro original
            Book libroOriginal = new nl.siegmann.epublib.epub.EpubReader()
                    .readEpub(new FileInputStream(rutaEpubOriginal.toFile()));

            // 2. Crear un libro nuevo
            Book libroDividido = new Book();

            // 3. Copiar los metadatos
            libroDividido.setMetadata(libroOriginal.getMetadata());
            libroDividido.getMetadata().setTitles(new java.util.ArrayList<String>());
            libroDividido.getMetadata().addTitle(libroOriginal.getTitle() + " (Parte " + inicio + "-" + fin + ")");

            // 4. Obtener la lista de capítulos (Spine)
            List<Resource> todosLosCapitulos = libroOriginal.getContents();

            System.out.println("El libro original tiene " + todosLosCapitulos.size() + " secciones de contenido.");

            // Validar el rango
            if (inicio < 1 || fin > todosLosCapitulos.size() || inicio > fin) {
                System.err.println(
                        "Error: Rango inválido. El libro tiene de 1 a " + todosLosCapitulos.size() + " secciones.");
                return;
            }

            // 5. Añadir SÓLO los capítulos del rango
            for (int i = (inicio - 1); i <= (fin - 1); i++) {
                Resource capitulo = todosLosCapitulos.get(i);
                nl.siegmann.epublib.domain.TOCReference tocRef = findTocRefByResource(
                        libroOriginal.getTableOfContents().getTocReferences(), capitulo);

                // Obtenemos el título de la referencia (si existe)
                String tituloSeccion = (tocRef != null) ? tocRef.getTitle() : capitulo.getHref();
                libroDividido.addSection(tituloSeccion, capitulo);
                System.out.println("Añadiendo: " + tituloSeccion);
            }

            // 6. Guardar el nuevo libro
            new EpubWriter().write(libroDividido, new FileOutputStream(rutaEpubNuevo.toFile()));
            System.out.println("¡Éxito! Se ha creado el archivo dividido: " + nombreEpubNuevo);

        } catch (IOException e) {
            System.err.println("Error al dividir el EPUB: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private nl.siegmann.epublib.domain.TOCReference findTocRefByResource(
            List<nl.siegmann.epublib.domain.TOCReference> tocReferences,
            nl.siegmann.epublib.domain.Resource resource) {

        // Si la lista es nula o vacía, no hay nada que buscar.
        if (tocReferences == null || tocReferences.isEmpty()) {
            return null;
        }

        // Iteramos sobre cada entrada del TOC en este nivel
        for (nl.siegmann.epublib.domain.TOCReference tocRef : tocReferences) {

            // Comparamos el recurso de esta entrada con el que buscamos.
            if (resource.equals(tocRef.getResource())) {
                return tocRef; // ¡Encontrado!
            }

            // Si no es este, buscamos en los hijos (sub-capítulos) de esta entrada
            nl.siegmann.epublib.domain.TOCReference foundInChildren = findTocRefByResource(tocRef.getChildren(),
                    resource);

            // Si se encontró en la búsqueda recursiva, lo retornamos
            if (foundInChildren != null) {
                return foundInChildren;
            }
        }

        return null;
    }

    private void descomprimirEpub() {
        System.out.println("Introduce el nombre del archivo EPUB a descomprimir (ej: MiLibro.epub):");
        String nombreEpub = scanner.nextLine();
        Path rutaEpub = Paths.get(rutaCarpeta, nombreEpub);

        if (!Files.exists(rutaEpub)) {
            System.err.println("Error: El archivo " + nombreEpub + " no se encuentra.");
            return;
        }

        String nombreCarpetaDestino;
        if (nombreEpub.toLowerCase().endsWith(".epub")) {
            nombreCarpetaDestino = nombreEpub.substring(0, nombreEpub.length() - 5);
        } else {
            nombreCarpetaDestino = nombreEpub + "_extraido";
        }

        Path rutaDestino = Paths.get(rutaCarpeta, nombreCarpetaDestino);
        if (Files.exists(rutaDestino)) {
            System.err.println("Error: La carpeta de destino '" + nombreCarpetaDestino + "' ya existe.");
            System.err.println("Por favor, renómbrala o bórrala y vuelve a intentarlo.");
            return;
        }

        System.out.println("Descomprimiendo en: " + rutaDestino.toAbsolutePath());

        try {
            Files.createDirectories(rutaDestino);

            // 2. Abrir el archivo EPUB como un ZipInputStream
            try (ZipInputStream zis = new ZipInputStream(
                    new BufferedInputStream(new FileInputStream(rutaEpub.toFile())))) {

                ZipEntry zipEntry;
                byte[] buffer = new byte[1024];

                // 3. Recorrer cada "entrada" (archivo/carpeta) dentro del ZIP
                while ((zipEntry = zis.getNextEntry()) != null) {

                    Path nuevoArchivo = rutaDestino.resolve(zipEntry.getName());
                    if (!nuevoArchivo.toAbsolutePath().startsWith(rutaDestino.toAbsolutePath())) {
                        throw new IOException("Entrada de ZIP peligrosa: " + zipEntry.getName());
                    }

                    if (zipEntry.isDirectory()) {
                        // 5. Si es un directorio, lo creamos
                        Files.createDirectories(nuevoArchivo);
                    } else {
                        // 6. Si es un archivo:
                        Files.createDirectories(nuevoArchivo.getParent());

                        try (FileOutputStream fos = new FileOutputStream(nuevoArchivo.toFile())) {
                            int len;
                            while ((len = zis.read(buffer)) > 0) {
                                fos.write(buffer, 0, len);
                            }
                        }
                    }
                    zis.closeEntry();
                }
            }

            System.out.println("¡EPUB descomprimido con éxito en la carpeta '" + nombreCarpetaDestino + "'!");

        } catch (IOException e) {
            System.err.println("Error al descomprimir el EPUB: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void reempaquetarComoEpub() {
        System.out.println("Introduce el nombre de la CARPETA que quieres empaquetar (ej: MiLibro_extraido):");
        String nombreCarpetaFuente = scanner.nextLine();
        Path rutaCarpetaFuente = Paths.get(rutaCarpeta, nombreCarpetaFuente);

        // --- Validaciones Esenciales ---
        if (!Files.isDirectory(rutaCarpetaFuente)) {
            System.err.println("Error: La carpeta '" + nombreCarpetaFuente + "' no existe.");
            return;
        }
        Path rutaMimetype = rutaCarpetaFuente.resolve("mimetype");
        if (!Files.exists(rutaMimetype)) {
            System.err.println("Error: La carpeta no contiene el archivo 'mimetype' en su raíz.");
            return;
        }
        if (!Files.isDirectory(rutaCarpetaFuente.resolve("META-INF"))) {
            System.err.println("Error: La carpeta no contiene el directorio 'META-INF'.");
            return;
        }
        // ---------------------------------

        System.out.println("Introduce el nombre del nuevo archivo EPUB de salida (ej: MiLibro_final.epub):");
        String nombreEpubSalida = scanner.nextLine();
        Path rutaEpubSalida = Paths.get(rutaCarpeta, nombreEpubSalida);

        try (
                // 1. Creamos el archivo .epub de salida
                FileOutputStream fos = new FileOutputStream(rutaEpubSalida.toFile());
                ZipOutputStream zos = new ZipOutputStream(fos)) {
            System.out.println("Empaquetando...");

            // --- REGLA 1 y 2: Añadir 'mimetype' PRIMERO y SIN COMPRESIÓN ---

            // Leemos los bytes del archivo mimetype
            byte[] mimetypeBytes = Files.readAllBytes(rutaMimetype);

            // Creamos una entrada Zip
            ZipEntry mimetypeEntry = new ZipEntry("mimetype");

            // REGLA 2: Sin compresión (STORED)
            mimetypeEntry.setMethod(ZipEntry.STORED);

            // Para el método STORED, necesitamos saber el tamaño y el CRC-32 ANTES de
            // escribirlo
            mimetypeEntry.setSize(mimetypeBytes.length);
            mimetypeEntry.setCompressedSize(mimetypeBytes.length);
            CRC32 crc = new CRC32();
            crc.update(mimetypeBytes);
            mimetypeEntry.setCrc(crc.getValue());

            // REGLA 1: Escribimos la entrada 'mimetype' PRIMERO
            zos.putNextEntry(mimetypeEntry);
            zos.write(mimetypeBytes);
            zos.closeEntry();

            // --- FIN DE REGLAS ESPECIALES ---

            // 3. Añadimos el RESTO de archivos (comprimidos)

            // Usamos Files.walk para recorrer recursivamente todas las carpetas y archivos
            try (Stream<Path> paths = Files.walk(rutaCarpetaFuente)) {

                paths.filter(Files::isRegularFile)
                        .filter(path -> !path.getFileName().toString().equals("mimetype"))
                        .forEach(path -> {
                            try {
                                // Creamos el nombre de la ruta dentro del ZIP
                                // ej: "OEBPS/Text/Capitulo0001.xhtml"
                                String zipPath = rutaCarpetaFuente.relativize(path).toString().replace(File.separator,
                                        "/"); // Usar / como en ZIP

                                ZipEntry entry = new ZipEntry(zipPath);
                                entry.setMethod(ZipEntry.DEFLATED);

                                zos.putNextEntry(entry);
                                Files.copy(path, zos);
                                zos.closeEntry();

                            } catch (IOException e) {
                                System.err.println("Error añadiendo el archivo: " + path);
                            }
                        });
            }

            System.out.println("¡EPUB re-empaquetado con éxito en '" + nombreEpubSalida + "'!");

        } catch (IOException e) {
            System.err.println("Error al crear el archivo EPUB: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void registrarProblema(Map<File, List<String>> mapa, File archivo, Element elemento) {
        mapa.computeIfAbsent(archivo, k -> new ArrayList<>())
                .add("<" + elemento.tagName() + ">: \"" + elemento.text() + "\"");
    }
}
