package com.extractor.mi_extractor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import io.documentnode.epub4j.domain.Author;
import io.documentnode.epub4j.domain.Book;
import io.documentnode.epub4j.domain.MediaTypes;
import io.documentnode.epub4j.domain.Resource;
import io.documentnode.epub4j.epub.EpubWriter;

/**
 * Creación de archivos EPUB a partir de la carpeta de trabajo: submenú y las tres vías
 * (EPUB 2 con epub4j, EPUB 3 nativo, y re-empaquetado de la estructura existente). Extraído
 * de {@link EditorXHTML} sin cambios de comportamiento. Trabaja sobre los .xhtml de la
 * carpeta; no toca el EPUB cargado en memoria del editor.
 */
class CreadorEpub {
private static final Logger log = LoggerFactory.getLogger(CreadorEpub.class);

    private final java.util.Scanner scanner;
    private final String rutaCarpeta;
    private final File[] archivosXHTML;

    CreadorEpub(java.util.Scanner scanner, String rutaCarpeta, File[] archivosXHTML) {
        this.scanner = scanner;
        this.rutaCarpeta = rutaCarpeta;
        this.archivosXHTML = archivosXHTML;
    }

    void mostrarSubMenuCrearEpub() {
        System.out.println("\n--- Crear Archivo EPUB ---");
        System.out.println("¿Cómo quieres crear el EPUB?");
        System.out.println("1. Desde cero EPUB 2 (epub4j, usando los .xhtml de OEBPS/Text)");
        System.out.println("2. Re-empaquetar estructura existente (carpeta con mimetype, META-INF, OEBPS)");
        System.out.println("3. Desde cero EPUB 3 (nativo: OPF 3.0 + nav.xhtml, usando los .xhtml de OEBPS/Text)");
        System.out.println("0. Cancelar");
        System.out.print("Elige una opción: ");

        int opcion;
        try {
            opcion = scanner.nextInt();
        } catch (java.util.InputMismatchException e) {
            opcion = -1;
        }
        scanner.nextLine(); // Limpiar buffer

        switch (opcion) {
            case 1:
                crearEpubConEpublib();
                break;
            case 2:
                crearEpubDesdeCarpeta();
                break;
            case 3:
                crearEpub3DesdeCero();
                break;
            case 0:
                System.out.println("Creación cancelada.");
                break;
            default:
                System.out.println("Opción no válida.");
        }
    }

    /**
     * Crea un EPUB 3 NATIVO (sin epub4j) con el empaquetador propio. Pide los mismos metadatos
     * que la opción 1 (mismos nombres de variables) y delega el ensamblado en EmpaquetadorEpub3.
     */
    private void crearEpub3DesdeCero() {
        System.out.println("--- Crear EPUB 3 (nativo) ---");
        System.out.println("Se escanearán automáticamente OEBPS/Styles, Fonts, Images y Text.");

        System.out.println("Introduce el título del libro:");
        String tituloLibro = scanner.nextLine();
        System.out.println("Introduce el autor:");
        String autorLibro = scanner.nextLine();
        System.out.println("Introduce la sinopsis (deja en blanco si no hay):");
        String sinopsis = scanner.nextLine();
        System.out.println("Introduce los géneros (separados por coma):");
        String generosStr = scanner.nextLine();
        System.out.println("(Opcional) Introduce el sitio de extracción:");
        String sitioExtraccion = scanner.nextLine();
        System.out.println("(Opcional) Introduce tu nombre como creador del archivo:");
        String creadorArchivo = scanner.nextLine();
        System.out.println("Introduce el nombre del archivo de la portada (ej: portada.jpg), o vacío:");
        System.out.println("(Debe estar en OEBPS/Images)");
        String nombrePortada = scanner.nextLine();
        System.out.println("Introduce el nombre del archivo de salida (ej: MiLibro.epub):");
        String archivoSalida = scanner.nextLine();
        if (archivoSalida == null || archivoSalida.isBlank()) {
            System.out.println("Nombre de archivo no válido. Cancelando.");
            return;
        }
        if (!archivoSalida.toLowerCase().endsWith(".epub")) {
            archivoSalida = archivoSalida + ".epub";
        }

        try {
            EmpaquetadorEpub3.Metadatos meta = new EmpaquetadorEpub3.Metadatos();
            meta.titulo = tituloLibro;
            meta.autor = autorLibro;
            meta.sinopsis = sinopsis;
            meta.generosCsv = generosStr;
            meta.idioma = "es";
            if (nombrePortada != null && !nombrePortada.isBlank()) {
                meta.coverHref = "Images/" + nombrePortada.trim().replace(File.separator, "/");
            }

            // Página de información (reusa el mismo generador que la opción 1).
            String paginaInfo = generarPaginaMetadatosXHTML(tituloLibro, autorLibro, sinopsis,
                    generosStr, sitioExtraccion, creadorArchivo, this.archivosXHTML);

            java.util.List<File> capitulos = java.util.Arrays.asList(this.archivosXHTML);
            Path salida = Paths.get(this.rutaCarpeta, archivoSalida);

            new EmpaquetadorEpub3().crear(Paths.get(this.rutaCarpeta), meta, capitulos, paginaInfo, salida);

            System.out.println("\n-----------------------------------");
            System.out.println("¡EPUB 3 creado con éxito!");
            System.out.println("Ubicación: " + salida);
            System.out.println("-----------------------------------");
        } catch (Exception e) {
            System.err.println("Error creando el EPUB 3: " + e.getMessage());
        }
    }

    private void añadirRecursosDeCarpeta(Book libro, Path rutaOEBPS, String subcarpeta) {
        Path rutaCompleta = rutaOEBPS.resolve(subcarpeta);
        if (!Files.isDirectory(rutaCompleta)) {
            System.out.println("[Info] No se encontró la carpeta: " + subcarpeta + ". Omitiendo.");
            return;
        }

        try (Stream<Path> paths = Files.walk(rutaCompleta)) {
            paths.filter(Files::isRegularFile).forEach(path -> {
                try {
                    // Crear el 'href' relativo, ej: "Styles/style.css" o "Images/portada.jpg"
                    String href = rutaOEBPS.relativize(path).toString().replace(File.separator, "/");

                    // Crear el recurso
                    Resource res = new Resource(new FileInputStream(path.toFile()), href);
                    // Determinar tipo (CSS, JPG, TTF, etc.) por la extensión
                    res.setMediaType(MediaTypes.determineMediaType(path.getFileName().toString()));

                    // Añadir el recurso al libro
                    libro.getResources().add(res);
                    System.out.println("Recurso añadido: " + href);

                } catch (IOException e) {
                    System.err.println("Error al añadir recurso " + path.getFileName() + ": " + e.getMessage());
                }
            });
        } catch (IOException e) {
            System.err.println("Error al escanear la carpeta '" + subcarpeta + "': " + e.getMessage());
        }
    }

    private void crearEpubConEpublib() {
        System.out.println("--- Creando EPUB (Modo Epub4j) ---");
        System.out.println("Se escanearán automáticamente las carpetas OEBPS/Styles, Fonts, Images y Text.");

        // --- 1. Pedir Metadatos ---
        System.out.println("Introduce el título del libro:");
        String tituloLibro = scanner.nextLine();
        System.out.println("Introduce el autor:");
        String autorLibro = scanner.nextLine();
        System.out.println("Introduce la sinopsis (deja en blanco si no hay):");
        String sinopsis = scanner.nextLine();
        System.out.println("Introduce los géneros (separados por coma):");
        String generosStr = scanner.nextLine();
        System.out.println("(Opcional) Introduce el sitio de extracción:");
        String sitioExtraccion = scanner.nextLine();
        System.out.println("(Opcional) Introduce tu nombre como creador del archivo:");
        String creadorArchivo = scanner.nextLine();

        try {
            Book libro = new Book();

            // --- 2. Asignar Metadatos ---
            libro.getMetadata().addTitle(tituloLibro);
            libro.getMetadata().addAuthor(new Author(autorLibro)); // Uso de clase Author de epub4j
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

            // --- 3. Escanear y Añadir Recursos (Styles, Fonts, Images) ---
            Path rutaOEBPS = Paths.get(this.rutaCarpeta, "OEBPS");
            añadirRecursosDeCarpeta(libro, rutaOEBPS, "Styles");
            añadirRecursosDeCarpeta(libro, rutaOEBPS, "Fonts");
            añadirRecursosDeCarpeta(libro, rutaOEBPS, "Images");

            // --- 4. PREGUNTAR POR LA PORTADA ---
            System.out.println("Introduce el nombre del archivo de la portada (ej: portada.jpg):");
            System.out.println("(Debe ser uno de los archivos listados arriba en OEBPS/Images)");
            String nombrePortada = scanner.nextLine();
            if (nombrePortada != null && !nombrePortada.isBlank()) {
                String hrefPortada = "Images/" + nombrePortada.replace(File.separator, "/");
                Resource coverResource = libro.getResources().getByHref(hrefPortada);
                if (coverResource != null) {
                    libro.setCoverImage(coverResource);
                    System.out.println("¡Portada establecida con éxito: " + hrefPortada);
                } else {
                    System.err.println("Advertencia: No se encontró el recurso '" + hrefPortada + "'.");
                }
            } else {
                System.out.println("[Info] No se especificó portada.");
            }

            // --- 5. Añadir Página de Información (CON sinopsis y géneros) ---
            System.out.println("Generando página de información...");
            String paginaMetaHtml = generarPaginaMetadatosXHTML(
                    tituloLibro, autorLibro, sinopsis, generosStr,
                    sitioExtraccion, creadorArchivo, this.archivosXHTML);
            Resource paginaMetaResource = new Resource(paginaMetaHtml.getBytes(StandardCharsets.UTF_8),
                    "Text/info_page.xhtml");
            paginaMetaResource.setMediaType(MediaTypes.XHTML);
            libro.addSection("Información del Libro", paginaMetaResource);

            // --- 6. Añadir Todos los Capítulos (de OEBPS/Text) ---
            System.out.println("Añadiendo " + this.archivosXHTML.length + " archivos de capítulo...");
            for (File archivo : this.archivosXHTML) {
                String href = "Text/" + archivo.getName();
                Resource res = new Resource(new FileInputStream(archivo), href);
                res.setMediaType(MediaTypes.XHTML);
                libro.addSection(archivo.getName().replace(".xhtml", ""), res);
            }

            // --- 7. Pedir Nombre de Salida y Escribir ---
            System.out.println("Introduce el nombre del archivo de salida (ej: MiLibro.epub):");
            String archivoSalida = scanner.nextLine();
            if (archivoSalida.isBlank()) {
                System.out.println("Nombre de archivo no válido. Cancelando.");
                return;
            }

            EpubWriter epubWriter = new EpubWriter();
            epubWriter.write(libro, new FileOutputStream(new File(rutaCarpeta, archivoSalida)));

            System.out.println("\n-----------------------------------");
            System.out.println("¡EPUB (Modo Epub4j) creado con éxito!: " + archivoSalida);
            System.out.println("-----------------------------------");

        } catch (IOException e) {
            System.err.println("Error al crear el EPUB: " + e.getMessage());
            log.error("Excepción no controlada", e);
        }
    }

    private String generarPaginaMetadatosXHTML(String title, String author, String synopsis, String genres,
            String sitioExtraccion, String creadorArchivo, File[] capitulos) {
        int startChapNum = 0;
        int endChapNum = 0;
        Pattern numPattern = Pattern.compile("(\\d+)");

        if (capitulos != null && capitulos.length > 0) {
            try {
                // Obtener número del primer archivo
                Matcher mStart = numPattern.matcher(capitulos[0].getName());
                if (mStart.find())
                    startChapNum = Integer.parseInt(mStart.group(1));
            } catch (Exception e) {
                startChapNum = 1;
            }

            try {
                // Obtener número del último archivo
                Matcher mEnd = numPattern.matcher(capitulos[capitulos.length - 1].getName());
                if (mEnd.find())
                    endChapNum = Integer.parseInt(mEnd.group(1));
            } catch (Exception e) {
                endChapNum = capitulos.length;
            }
        }

        String authorFinal = (author != null && !author.isBlank()) ? author : "Desconocido";
        String synopsisFinal = (synopsis != null && !synopsis.isBlank()) ? synopsis : "No disponible.";
        String genresFinal = (genres != null && !genres.isBlank()) ? genres : "No especificados.";

        // --- Generar el HTML (usando tu plantilla) ---
        StringBuilder sb = new StringBuilder();
        String style = "body { font-family: sans-serif; margin: 2em; } h1 { text-align: center; } h2 { border-bottom: 1px solid #ccc; padding-bottom: 0.2em; } dt { font-weight: bold; margin-top: 0.5em; } dd { margin-left: 0; margin-bottom: 0.5em; }";

        sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
                .append("<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.1//EN\"\n")
                .append("  \"http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd\">\n")
                .append("<html xmlns=\"http://www.w3.org/1999/xhtml\" xml:lang=\"es\">\n")
                .append("<head>\n")
                .append("  <title>Información del Libro</title>\n")
                .append("  <link rel=\"stylesheet\" type=\"text/css\" href=\"../Styles/stylesheet.css\" />\n")
                .append("  <style type=\"text/css\">").append(style).append("</style>\n")
                .append("</head>\n")
                .append("<body>\n")
                .append("  <h1>").append(title != null ? title : "Libro").append("</h1>\n")
                .append("  <hr/>\n")
                .append("  <h2>Ficha Informativa</h2>\n")
                .append("  <dl>\n")
                .append("    <dt>Capítulos Incluidos:</dt>\n")
                .append("    <dd>").append(startChapNum).append(" - ").append(endChapNum).append(" (Total: ")
                .append(capitulos.length).append(")</dd>\n")
                .append("  </dl>\n")
                .append("  <h2>Información General</h2>\n")
                .append("  <dl>\n")
                .append("    <dt>Título Original:</dt>\n")
                .append("    <dd>").append(title != null ? title : "No disponible").append("</dd>\n")
                .append("    <dt>Autor:</dt>\n")
                .append("    <dd>").append(authorFinal).append("</dd>\n")
                .append("    <dt>Géneros:</dt>\n")
                .append("    <dd>").append(genresFinal).append("</dd>\n")
                .append("    <dt>Sinopsis:</dt>\n")
                .append("    <dd>").append(synopsisFinal).append("</dd>\n");

        if (sitioExtraccion != null && !sitioExtraccion.isBlank()) {
            sb.append("    <dt>Sitio de Extracción:</dt>\n")
                    .append("    <dd>").append(sitioExtraccion).append("</dd>\n");
        }
        if (creadorArchivo != null && !creadorArchivo.isBlank()) {
            sb.append("    <dt>Archivo generado por:</dt>\n")
                    .append("    <dd>").append(creadorArchivo).append("</dd>\n");
        }

        sb.append("  </dl>\n")
                .append("</body>\n")
                .append("</html>");

        return sb.toString();
    }

    private void crearEpubDesdeCarpeta() {
        Path rutaCarpetaFuente = Paths.get(this.rutaCarpeta);

        // --- Validaciones Esenciales ---
        if (!Files.isDirectory(rutaCarpetaFuente)) {
            System.err.println("Error: La carpeta de trabajo '" + this.rutaCarpeta + "' no es válida.");
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
        if (!Files.isDirectory(rutaCarpetaFuente.resolve("OEBPS"))) {
            System.err.println("Error: La carpeta no contiene el directorio 'OEBPS'.");
            return;
        }

        System.out.println("Introduce el nombre del nuevo archivo EPUB de salida (ej: "
                + rutaCarpetaFuente.getFileName().toString() + "_final.epub):");
        String nombreEpubSalida = scanner.nextLine();
        // Si el usuario no escribe nada, usar un nombre por defecto
        if (nombreEpubSalida.isBlank()) {
            nombreEpubSalida = rutaCarpetaFuente.getFileName().toString() + "_final.epub";
        }
        Path rutaEpubSalida = Paths.get(rutaCarpeta, nombreEpubSalida);

        try (
                FileOutputStream fos = new FileOutputStream(rutaEpubSalida.toFile());
                ZipOutputStream zos = new ZipOutputStream(fos)) {
            System.out.println("Empaquetando la estructura de carpetas...");

            // --- REGLA 1 y 2: Añadir 'mimetype' PRIMERO y SIN COMPRESIÓN ---
            byte[] mimetypeBytes = Files.readAllBytes(rutaMimetype);
            ZipEntry mimetypeEntry = new ZipEntry("mimetype");
            mimetypeEntry.setMethod(ZipEntry.STORED);
            mimetypeEntry.setSize(mimetypeBytes.length);
            mimetypeEntry.setCompressedSize(mimetypeBytes.length);
            CRC32 crc = new CRC32();
            crc.update(mimetypeBytes);
            mimetypeEntry.setCrc(crc.getValue());
            zos.putNextEntry(mimetypeEntry);
            zos.write(mimetypeBytes);
            zos.closeEntry();

            // --- 3. Añadimos el RESTO de carpetas y archivos (comprimidos) ---
            // ¡Aquí usamos el método auxiliar!
            Path rutaMetaInf = rutaCarpetaFuente.resolve("META-INF");
            Path rutaOebps = rutaCarpetaFuente.resolve("OEBPS");

            if (Files.isDirectory(rutaMetaInf)) {
                añadirDirectorioAZip(rutaMetaInf, rutaCarpetaFuente, zos);
            }
            if (Files.isDirectory(rutaOebps)) {
                añadirDirectorioAZip(rutaOebps, rutaCarpetaFuente, zos);
            }

            System.out.println("¡EPUB re-empaquetado con éxito en '" + nombreEpubSalida + "'!");

        } catch (IOException e) {
            System.err.println("Error al crear el archivo EPUB: " + e.getMessage());
            log.error("Excepción no controlada", e);
        }
    }

    private void añadirDirectorioAZip(Path directorioFuente, Path base, ZipOutputStream zos) throws IOException {
        try (Stream<Path> paths = Files.walk(directorioFuente)) {
            paths.forEach(path -> {
                try {
                    // Calcula la ruta relativa para usarla como nombre de entrada en el ZIP
                    // Ej: Si base es "C:/MiEpub" y path es "C:/MiEpub/OEBPS/Text/cap1.xhtml",
                    // zipPath será "OEBPS/Text/cap1.xhtml"
                    String zipPath = base.relativize(path).toString().replace(File.separator, "/");
                    if (zipPath.isEmpty()) {
                        return;
                    }

                    if (Files.isDirectory(path)) {
                        if (!zipPath.endsWith("/")) {
                            zipPath += "/";
                        }

                        ZipEntry entry = new ZipEntry(zipPath);
                        zos.putNextEntry(entry);
                        zos.closeEntry();
                    } else {

                        ZipEntry entry = new ZipEntry(zipPath);
                        entry.setMethod(ZipEntry.DEFLATED);
                        zos.putNextEntry(entry);

                        Files.copy(path, zos);
                        zos.closeEntry();
                    }
                } catch (IOException e) {
                    throw new RuntimeException("Error añadiendo " + path + " al ZIP", e);
                }
            });
        } catch (RuntimeException e) {
            if (e.getCause() instanceof IOException) {
                throw (IOException) e.getCause();
            } else {
                throw e;
            }
        }
    }
}
