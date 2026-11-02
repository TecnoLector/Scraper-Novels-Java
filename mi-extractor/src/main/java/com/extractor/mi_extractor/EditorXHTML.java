package com.extractor.mi_extractor;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import io.documentnode.epub4j.domain.Book;
import io.documentnode.epub4j.domain.Resource;
import io.documentnode.epub4j.epub.EpubReader;
import io.documentnode.epub4j.epub.EpubWriter;
import io.documentnode.epub4j.domain.MediaTypes;

public class EditorXHTML {

    private Scanner scanner;
    private String rutaCarpeta;
    private File[] archivosXHTML;
    private Path rutaEpub;
    private Book libro;
    private boolean cambiosPendientes = false;

    // El constructor ahora detecta XHTML y/o EPUB
    public EditorXHTML(Scanner scanner) {
        this.scanner = scanner;
        System.out.println("Introduce la ruta de la carpeta de trabajo (donde están los .xhtml o el .epub):");
        this.rutaCarpeta = scanner.nextLine();

        actualizarListaXHTML();
        cargarLibroEpub();
    }

    // Método estático para iniciar desde App.java
    public static void iniciar(Scanner scanner) {
        EditorXHTML editor = new EditorXHTML(scanner);
        // Verificar si hay algo con qué trabajar (XHTML o EPUB)
        if ((editor.archivosXHTML == null || editor.archivosXHTML.length == 0) && editor.libro == null) {
            System.err.println("No se encontraron archivos .xhtml ni un archivo .epub válido en la carpeta.");
            System.err.println("Volviendo al menú principal.");
            return;
        }
        editor.mostrarMenu();
    }

    // Carga/actualiza la lista de archivos .xhtml
    private void actualizarListaXHTML() {
        Path rutaText = Paths.get(this.rutaCarpeta, "OEBPS", "Text");
        File carpetaText = rutaText.toFile();
        if (carpetaText.exists() && carpetaText.isDirectory()) {
            this.archivosXHTML = carpetaText.listFiles((dir, name) -> name.toLowerCase().endsWith(".xhtml"));
            if (this.archivosXHTML != null) {
                Arrays.sort(this.archivosXHTML, Comparator.comparing(File::getName));
            } else {
                this.archivosXHTML = new File[0];
            }
        } else {
            this.archivosXHTML = new File[0];
        }
    }

    // Carga el archivo EPUB si existe
    private void cargarLibroEpub() {
        Path rutaCarpetaPath = Paths.get(this.rutaCarpeta);
        File carpeta = rutaCarpetaPath.toFile();
        this.libro = null;
        this.rutaEpub = null;
        if (!carpeta.exists() || !carpeta.isDirectory())
            return;
        File[] archivosEpub = carpeta.listFiles((dir, name) -> name.toLowerCase().endsWith(".epub"));
        File archivoACargar = null;
        if (archivosEpub == null || archivosEpub.length == 0) {
            System.out.println("[Info] No se encontró ningún archivo .epub en la carpeta.");
            return;
        } else if (archivosEpub.length == 1) {
            archivoACargar = archivosEpub[0];
        } else {
            System.out.println("Se encontraron varios archivos .epub en la carpeta:");
            for (int i = 0; i < archivosEpub.length; i++) {
                System.out.println((i + 1) + ". " + archivosEpub[i].getName());
            }
            System.out.print("¿Cuál quieres editar? (Introduce el número): ");
            int seleccion;
            try {
                seleccion = scanner.nextInt();
                scanner.nextLine();
                if (seleccion >= 1 && seleccion <= archivosEpub.length) {
                    archivoACargar = archivosEpub[seleccion - 1];
                } else {
                    System.err.println("Selección inválida.");
                }
            } catch (java.util.InputMismatchException e) {
                System.err.println("Entrada inválida.");
                scanner.nextLine();
            }
        }

        if (archivoACargar != null) {
            this.rutaEpub = archivoACargar.toPath();
            try {
                EpubReader epubReader = new EpubReader();
                this.libro = epubReader.readEpub(new FileInputStream(this.rutaEpub.toFile()));
                System.out.println("Archivo EPUB '" + archivoACargar.getName() + "' cargado con éxito.");
            } catch (IOException e) {
                System.err.println(
                        "Error al cargar el archivo EPUB '" + archivoACargar.getName() + "': " + e.getMessage());
                this.libro = null;
                this.rutaEpub = null;
            }
        } else {
            System.err.println("No se seleccionó un archivo EPUB válido para editar.");
        }
    }

    public void mostrarMenu() {
        while (true) {
            actualizarListaXHTML();
            boolean hayXHTML = (archivosXHTML != null && archivosXHTML.length > 0);
            boolean hayEPUB = (this.libro != null);
            String estadoEPUB = (hayEPUB && cambiosPendientes) ? " (EPUB con cambios SIN GUARDAR)" : "";

            System.out.println("\n--- Editor XHTML / EPUB ---" + estadoEPUB);
            System.out.println("Carpeta: " + this.rutaCarpeta);

            // --- Opciones para XHTML ---
            if (hayXHTML) {
                System.out.println("\n--- Herramientas para Archivos .XHTML (" + archivosXHTML.length + ") ---");
                System.out.println("1. Estandarizar Títulos (.xhtml)");
                System.out.println("2. Renombrar Archivos (.xhtml)");
                System.out.println("3. Limpiar Encabezados Basura (.xhtml)");
                System.out.println("4. Validar Títulos (.xhtml)");
                System.out.println("5. Vincular Hoja de Estilo (CSS) a .xhtml");
                System.out.println("6. Generar Archivo de Índice (toc.xhtml)");
                System.out.println("7. CREAR Archivo EPUB...");
                System.out.println("8. Reparar Errores Comunes (.xhtml)");
                System.out.println("9. Analizar Faltantes");
            } else {
                System.out.println("\n[No se encontraron archivos .xhtml]");
            }

            // --- Opciones para EPUB ---
            if (hayEPUB) {
                System.out.println("\n--- Herramientas para el EPUB Cargado (" + this.rutaEpub.getFileName() + ") ---");
                System.out.println("11. Estandarizar Títulos (en EPUB)");
                System.out.println("12. Limpiar Encabezados Basura (en EPUB)");
                System.out.println("13. Validar Títulos (en EPUB)");
                System.out.println("14. Añadir/Actualizar Portada (en EPUB)");
                System.out.println("15. Añadir/Actualizar Sinopsis y Géneros (en EPUB)");
                System.out.println("19. GUARDAR CAMBIOS en el archivo EPUB");
            } else {
                System.out.println("\n[No se cargó ningún archivo .epub para editar]");
            }

            System.out.println("\n-----------------------------------");
            System.out.println("0. Volver al menú principal");
            System.out.print("Elige una herramienta: ");

            int opcion;
            try {
                opcion = scanner.nextInt();
            } catch (java.util.InputMismatchException e) {
                opcion = -1;
            }
            scanner.nextLine();

            EditorXhtmlArchivos xhtmlOps = new EditorXhtmlArchivos(scanner, rutaCarpeta, archivosXHTML);

            // --- Lógica del Switch ---
            switch (opcion) {
                case 1:
                    if (hayXHTML)
                        xhtmlOps.estandarizarTitulosXHTML();
                    else
                        System.out.println("Opción no disponible.");
                    break;
                case 2:
                    if (hayXHTML)
                        xhtmlOps.renombrarArchivosXHTML();
                    else
                        System.out.println("Opción no disponible.");
                    break;
                case 3:
                    if (hayXHTML)
                        xhtmlOps.limpiarEncabezadosXHTML();
                    else
                        System.out.println("Opción no disponible.");
                    break;
                case 4:
                    if (hayXHTML)
                        xhtmlOps.validarTitulosXHTML();
                    else
                        System.out.println("Opción no disponible.");
                    break;
                case 5:
                    if (hayXHTML)
                        xhtmlOps.vincularCssXHTML();
                    else
                        System.out.println("Opción no disponible.");
                    break;
                case 6:
                    if (hayXHTML)
                        xhtmlOps.generarTocXHTML();
                    else
                        System.out.println("Opción no disponible.");
                    break;
                case 7:
                    if (hayXHTML)
                        new CreadorEpub(scanner, rutaCarpeta, archivosXHTML).mostrarSubMenuCrearEpub();
                    else
                        sinOpcion();
                    break;
                case 8:
                    if (hayXHTML)
                        xhtmlOps.repararErroresComunes();
                    else
                        sinOpcion();
                    break;
                case 9:
                    if (hayXHTML)
                        xhtmlOps.analizarCapitulosFaltantes();
                    else
                        sinOpcion();
                    break;

                // Opciones EPUB (solo si hayEPUB)
                case 11:
                    if (hayEPUB) {
                        estandarizarTitulosEPUB();
                        cambiosPendientes = true;
                    } else
                        System.out.println("Opción no disponible.");
                    break;
                case 12:
                    if (hayEPUB) {
                        limpiarEncabezadosEPUB();
                        cambiosPendientes = true;
                    } else
                        System.out.println("Opción no disponible.");
                    break;
                case 13:
                    if (hayEPUB)
                        validarTitulosEPUB();
                    else
                        System.out.println("Opción no disponible.");
                    break;
                case 14:
                    if (hayEPUB) {
                        actualizarPortadaEPUB();
                        cambiosPendientes = true;
                    } else
                        System.out.println("Opción no disponible.");
                    break;
                case 15:
                    if (hayEPUB) {
                        actualizarMetadatosEPUB();
                        cambiosPendientes = true;
                    } else
                        System.out.println("Opción no disponible.");
                    break;
                case 19:
                    if (hayEPUB) {
                        guardarCambiosEPUB();
                        cambiosPendientes = false;
                    } else
                        System.out.println("Opción no disponible.");
                    break;
                case 0: // Salir
                    if (hayEPUB && cambiosPendientes) {
                        System.out.print(
                                "ADVERTENCIA: Tienes cambios sin guardar en el EPUB. ¿Salir de todos modos? (s/n): ");
                        if (!scanner.nextLine().equalsIgnoreCase("s")) {
                            continue;
                        }
                    }
                    return;
                default:
                    System.out.println("Opción no válida.");
            }
        }
    }

    private void sinOpcion() {
        System.out.println("Opción no disponible (verifica si hay archivos .xhtml o .epub cargado).");
    }

    // --- MÉTODOS PARA XHTML (Renombrados con sufijo XHTML) ---
    private void guardarCambiosEPUB() {
        if (this.libro == null)
            return;
        try {
            System.out.println("Guardando cambios en " + this.rutaEpub.getFileName() + "...");
            EpubWriter epubWriter = new EpubWriter();
            epubWriter.write(this.libro, new FileOutputStream(this.rutaEpub.toFile()));
            System.out.println("¡Cambios guardados con éxito!");
        } catch (IOException e) {
            /* ... */ }
    }

    private void estandarizarTitulosEPUB() {
        Pattern patronExtraccion = Pattern.compile(".*Cap[ií]tulo\\s*(\\d+)[\\s:–—.-]?\\s*(.*)",
                Pattern.CASE_INSENSITIVE);
        int modificados = 0;
        System.out.println("Analizando títulos en memoria...");
        for (io.documentnode.epub4j.domain.TOCReference tocRef : this.libro.getTableOfContents().getTocReferences()) {
            Resource recurso = tocRef.getResource();
            if (recurso == null || !recurso.getMediaType().equals(MediaTypes.XHTML))
                continue;
            try {
                String contenidoHtml = new String(recurso.getData(), StandardCharsets.UTF_8);
                Document doc = Jsoup.parse(contenidoHtml);
                Element h1 = doc.selectFirst("h1");
                Element titleTag = doc.selectFirst("title");
                if (h1 != null && h1.hasText() && titleTag != null) {
                    String tituloOriginal = h1.text().trim();
                    Matcher matcher = patronExtraccion.matcher(tituloOriginal.replace("\uFFFD", "’"));
                    if (matcher.find()) {
                        String numero = matcher.group(1);
                        String nombre = matcher.group(2).trim();
                        if (nombre.isEmpty())
                            nombre = "(Título no encontrado)";
                        String tituloNuevo = "Capítulo " + numero + ": " + nombre;
                        if (!tituloOriginal.equals(tituloNuevo) || !titleTag.text().equals(tituloNuevo)) {
                            h1.text(tituloNuevo);
                            titleTag.text(tituloNuevo);
                            recurso.setData(doc.outerHtml().getBytes(StandardCharsets.UTF_8));
                            tocRef.setTitle(tituloNuevo);
                            System.out.println(tituloOriginal + " -> " + tituloNuevo);
                            modificados++;
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("Error procesando recurso: " + recurso.getHref());
            }
        }
        System.out.println(modificados + " títulos estandarizados en memoria.");
    }

    private void limpiarEncabezadosEPUB() {
        Pattern patronTituloValido = Pattern.compile("^Cap[ií]tulo\\s+\\d+.*\\w.*", Pattern.CASE_INSENSITIVE);
        String[] etiquetas = { "h1", "h2", "h3", "h4" };
        int modificados = 0;
        System.out.println("Limpiando encabezados basura en memoria...");
        for (Resource recurso : this.libro.getContents()) {
            if (recurso == null || !recurso.getMediaType().equals(MediaTypes.XHTML))
                continue;
            try {
                String contenidoHtml = new String(recurso.getData(), StandardCharsets.UTF_8);
                Document doc = Jsoup.parse(contenidoHtml);
                boolean modificado = false;
                for (String tag : etiquetas) {
                    Elements encabezados = doc.select(tag);
                    Element principal = null;
                    java.util.List<Element> paraBorrar = new java.util.ArrayList<>();
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
                    recurso.setData(doc.outerHtml().getBytes(StandardCharsets.UTF_8));
                    System.out.println("Encabezados limpiados en: " + recurso.getHref());
                    modificados++;
                }
            } catch (Exception e) {
                System.err.println("Error limpiando recurso: " + recurso.getHref());
            }
        }
        System.out.println(modificados + " archivos limpiados en memoria.");
    }

    private void validarTitulosEPUB() {
        System.out.println("\n--- Reporte de Validación de Títulos (en memoria) ---");
        Pattern patronTituloValido = Pattern.compile("^Cap[ií]tulo\\s+\\d+.*\\w.*", Pattern.CASE_INSENSITIVE);
        int titulosInvalidos = 0;
        int titulosNoCoinciden = 0;
        for (Resource recurso : this.libro.getContents()) {
            if (recurso == null || !recurso.getMediaType().equals(MediaTypes.XHTML))
                continue;
            try {
                String contenidoHtml = new String(recurso.getData(), StandardCharsets.UTF_8);
                Document doc = Jsoup.parse(contenidoHtml);
                Element h1Element = doc.selectFirst("h1");
                Element titleElement = doc.selectFirst("title");
                if (h1Element != null && h1Element.hasText()) {
                    String textoH1 = h1Element.text().trim();
                    if (!patronTituloValido.matcher(textoH1).find()) {
                        System.out.println(
                                "Archivo: " + recurso.getHref() + " -> Formato H1 Inválido: \"" + textoH1 + "\"");
                        titulosInvalidos++;
                    }
                } else {
                    System.out.println("Archivo: " + recurso.getHref() + " -> Error: No se encontró <h1>.");
                    titulosInvalidos++;
                }
                if (h1Element != null && titleElement != null) {
                    if (!h1Element.text().trim().equals(titleElement.text().trim())) {
                        System.out.println("Archivo: " + recurso.getHref() + " -> No coinciden:");
                        System.out.println("  -> H1:    \"" + h1Element.text().trim() + "\"");
                        System.out.println("  -> Title: \"" + titleElement.text().trim() + "\"");
                        titulosNoCoinciden++;
                    }
                } else {
                    System.out.println(
                            "Archivo: " + recurso.getHref() + " -> Error: Falta <h1> o <title> para comparar.");
                }
            } catch (Exception e) {
                System.err.println("Error validando recurso: " + recurso.getHref());
            }
        }
        if (titulosInvalidos == 0 && titulosNoCoinciden == 0) {
            System.out.println("¡Perfecto! Todos los títulos están validados y coinciden.");
        } else {
            System.out.println("Validación terminada: " + titulosInvalidos + " con formato inválido, "
                    + titulosNoCoinciden + " no coinciden.");
        }
    }

    private void actualizarPortadaEPUB() {
        System.out.println("Introduce el nombre de la imagen de portada (ej: portada.jpg):");
        System.out.println("(Debe estar en la carpeta: " + this.rutaCarpeta + ")");
        String nombrePortada = scanner.nextLine();
        Path rutaPortada = Paths.get(this.rutaCarpeta, nombrePortada);
        if (!Files.exists(rutaPortada)) {
            System.err.println("Error: No se encontró el archivo: " + rutaPortada);
            return;
        }
        try {
            Resource coverResource = new Resource(new FileInputStream(rutaPortada.toFile()),
                    "cover." + (nombrePortada.endsWith("png") ? "png" : "jpg"));
            if (nombrePortada.toLowerCase().endsWith(".png")) {
                coverResource.setMediaType(MediaTypes.PNG);
            } else {
                coverResource.setMediaType(MediaTypes.JPG);
            }
            this.libro.setCoverImage(coverResource);
            System.out.println("¡Imagen de portada actualizada en memoria!");
        } catch (IOException e) {
            System.err.println("Error al leer el archivo de portada: " + e.getMessage());
        }
    }

    private void actualizarMetadatosEPUB() {
        System.out.println("Introduce la sinopsis (deja en blanco para no cambiar):");
        String sinopsis = scanner.nextLine();
        System.out.println("Introduce los géneros (separados por coma, deja en blanco para no cambiar):");
        String generosStr = scanner.nextLine();
        if (sinopsis != null && !sinopsis.isEmpty()) {
            this.libro.getMetadata().setDescriptions(java.util.List.of(sinopsis));
            System.out.println("Sinopsis actualizada en memoria.");
        }
        if (generosStr != null && !generosStr.isEmpty()) {
            java.util.List<String> listaDeGeneros = new java.util.ArrayList<>();
            for (String genero : generosStr.split(",")) {
                listaDeGeneros.add(genero.trim());
            }
            this.libro.getMetadata().setSubjects(listaDeGeneros);
            System.out.println("Géneros actualizados en memoria.");
        }
    }

}