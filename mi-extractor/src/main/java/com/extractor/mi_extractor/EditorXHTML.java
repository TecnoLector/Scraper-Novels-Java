package com.extractor.mi_extractor;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URLEncoder;
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
import java.util.stream.Stream;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import nl.siegmann.epublib.domain.Book;
import nl.siegmann.epublib.domain.Resource;
import nl.siegmann.epublib.domain.TOCReference;
import nl.siegmann.epublib.epub.EpubReader;
import nl.siegmann.epublib.epub.EpubWriter;
import nl.siegmann.epublib.service.MediatypeService;

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

            // --- Lógica del Switch ---
            switch (opcion) {
                case 1:
                    if (hayXHTML)
                        estandarizarTitulosXHTML();
                    else
                        System.out.println("Opción no disponible.");
                    break;
                case 2:
                    if (hayXHTML)
                        renombrarArchivosXHTML();
                    else
                        System.out.println("Opción no disponible.");
                    break;
                case 3:
                    if (hayXHTML)
                        limpiarEncabezadosXHTML();
                    else
                        System.out.println("Opción no disponible.");
                    break;
                case 4:
                    if (hayXHTML)
                        validarTitulosXHTML();
                    else
                        System.out.println("Opción no disponible.");
                    break;
                case 5:
                    if (hayXHTML)
                        vincularCssXHTML();
                    else
                        System.out.println("Opción no disponible.");
                    break;
                case 6:
                    if (hayXHTML)
                        generarTocXHTML();
                    else
                        System.out.println("Opción no disponible.");
                    break;
                case 7:
                    if (hayXHTML)
                        mostrarSubMenuCrearEpub();
                    else
                        sinOpcion();
                    break;
                case 8:
                    if (hayXHTML)
                        repararErroresComunes();
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

    private void mostrarSubMenuCrearEpub() {
        System.out.println("\n--- Crear Archivo EPUB ---");
        System.out.println("¿Cómo quieres crear el EPUB?");
        System.out.println("1. Desde cero (usando los archivos .xhtml de la carpeta OEBPS/Text)");
        System.out.println("2. Re-empaquetar estructura existente (carpeta con mimetype, META-INF, OEBPS)");
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
            case 0:
                System.out.println("Creación cancelada.");
                break;
            default:
                System.out.println("Opción no válida.");
        }
    }

    private void sinOpcion() {
        System.out.println("Opción no disponible (verifica si hay archivos .xhtml o .epub cargado).");
    }

    // --- MÉTODOS PARA XHTML (Renombrados con sufijo XHTML) ---
    private void renombrarArchivosXHTML() {
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

    private void estandarizarTitulosXHTML() {
        // --- Patrones de Búsqueda ---
        // Patrón 1: "Capítulo 123: Nombre"
        Pattern patronConPalabra = Pattern.compile(".*Cap[ií]tulo\\s*(\\d+)[\\s:–—.-]?\\s*(.*)",
                Pattern.CASE_INSENSITIVE);
        // Patrón 2: "123: Nombre" (con separador)
        Pattern patronSinPalabraSep = Pattern.compile("^(\\d+)[\\s:–—.-]+\\s*([^<]+.*)");
        // Patrón 3: "123 Nombre" (como tu ejemplo: 1013 Crecimiento Individual)
        Pattern patronSinPalabraSimple = Pattern.compile("^(\\d+)\\s+([^<]+.*)");
        String[] selectoresCandidatos = {
                "h1", "h2", "h3", "h4", "p > strong", "p", "b"
        };

        Map<File, PropuestaCambio> cambiosPropuestos = new LinkedHashMap<>();

        System.out.println("Analizando títulos (con promoción a h1)...");
        for (File archivo : archivosXHTML) {
            try {
                String contenido = Files.readString(archivo.toPath(), StandardCharsets.UTF_8);
                Document doc = Jsoup.parse(contenido);
                Element tituloElement = null;
                String selectorEncontrado = null;

                for (String selector : selectoresCandidatos) {
                    tituloElement = doc.selectFirst(selector);
                    if (tituloElement != null) {
                        selectorEncontrado = selector;
                        break;
                    }
                }

                if (tituloElement == null) {
                    System.err.println(
                            "Advertencia: No se encontró <h1>, <h2>, <h3> o <p><strong> en " + archivo.getName());
                    continue;
                }

                String tituloOriginal = tituloElement.text().trim().replace("\uFFFD", "’");
                Matcher m1 = patronConPalabra.matcher(tituloOriginal);
                Matcher m2 = patronSinPalabraSep.matcher(tituloOriginal);
                Matcher m3 = patronSinPalabraSimple.matcher(tituloOriginal);

                String numero = null;
                String nombre = null;

                if (m1.matches()) { // "Capítulo 123: Nombre"
                    numero = m1.group(1);
                    nombre = m1.group(2).trim();
                } else if (m2.matches()) { // "123: Nombre"
                    numero = m2.group(1);
                    nombre = m2.group(2).trim();
                } else if (m3.matches()) { // "123 Nombre"
                    numero = m3.group(1);
                    nombre = m3.group(2).trim();
                }

                if (numero != null && nombre != null) {
                    if (nombre.isEmpty())
                        nombre = "(Título no encontrado)";
                    String tituloNuevo = "Capítulo " + numero + ": " + nombre;

                    boolean debePromover = !tituloElement.tagName().equals("h1");
                    if (!tituloOriginal.equals(tituloNuevo) || debePromover) {
                        String cssSelector = tituloElement.cssSelector();
                        cambiosPropuestos.put(archivo,
                                new PropuestaCambio(cssSelector, tituloOriginal, tituloNuevo, debePromover));
                    }
                } else {
                    System.out.println(
                            "Info: Título no reconocido en " + archivo.getName() + ": \"" + tituloOriginal + "\"");
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

    private void limpiarEncabezadosXHTML() {
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

    private void validarTitulosXHTML() {
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

    private void vincularCssXHTML() {
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

    private void generarTocXHTML() {
        System.out.println("Generando 'toc.xhtml'...");
        StringBuilder tocBuilder = new StringBuilder();
        tocBuilder.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                .append("<!DOCTYPE html>\n")
                .append("<html xmlns=\"http://www.w3.org/1999/xhtml\" xmlns:epub=\"http://www.idpf.org/2007/ops\" lang=\"es\">\n")
                .append("<head>\n")
                .append("    <meta charset=\"UTF-8\"/>\n")
                .append("    <title>Tabla de Contenidos</title>\n")
                .append("    <link rel=\"stylesheet\" type=\"text/css\" href=\"Styles/stylesheet.css\"/>\n")
                .append("</head>\n")
                .append("<body>\n")
                .append("    <nav epub:type=\"toc\" id=\"toc\">\n")
                .append("        <h1>Índice</h1>\n")
                .append("        <ol>\n");

        for (File archivo : archivosXHTML) {
            if (archivo.getName().equalsIgnoreCase("toc.xhtml"))
                continue;

            try {
                Document doc = Jsoup.parse(archivo, "UTF-8");
                Element h1 = doc.selectFirst("h1");
                String tituloRaw = (h1 != null) ? h1.text() : archivo.getName().replace(".xhtml", "");
                String tituloLimpio = tituloRaw.replace("&", "&amp;")
                        .replace("<", "&lt;")
                        .replace(">", "&gt;")
                        .replace("\"", "&quot;");
                String nombreArchivo = archivo.getName();
                String hrefCodificado = URLEncoder.encode(nombreArchivo, StandardCharsets.UTF_8.toString())
                        .replace("+", "%20")
                        .replace("%2E", ".");

                tocBuilder.append("            <li><a href=\"").append(hrefCodificado).append("\">")
                        .append(tituloLimpio).append("</a></li>\n");

            } catch (IOException e) {
                System.err.println("Advertencia: No se pudo leer " + archivo.getName());
            }
        }

        tocBuilder.append("        </ol>\n").append("    </nav>\n").append("</body>\n").append("</html>");

        try {
            Files.writeString(Paths.get(rutaCarpeta, "toc.xhtml"), tocBuilder.toString(), StandardCharsets.UTF_8);
            System.out.println("'toc.xhtml' generado correctamente.");
        } catch (IOException e) {
            System.err.println("Error grave al guardar 'toc.xhtml': " + e.getMessage());
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
                    res.setMediaType(MediatypeService.determineMediaType(path.getFileName().toString()));

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
        System.out.println("--- Creando EPUB (Modo Epublib) ---");
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
            paginaMetaResource.setMediaType(MediatypeService.XHTML);
            libro.addSection("Información del Libro", paginaMetaResource);

            // --- 6. Añadir Todos los Capítulos (de OEBPS/Text) ---
            System.out.println("Añadiendo " + this.archivosXHTML.length + " archivos de capítulo...");
            for (File archivo : this.archivosXHTML) {
                String href = "Text/" + archivo.getName();
                Resource res = new Resource(new FileInputStream(archivo), href);
                res.setMediaType(MediatypeService.XHTML);
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
            System.out.println("¡EPUB (Modo Epublib) creado con éxito!: " + archivoSalida);
            System.out.println("-----------------------------------");

        } catch (IOException e) {
            System.err.println("Error al crear el EPUB: " + e.getMessage());
            e.printStackTrace();
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

    private void registrarProblema(Map<File, List<String>> mapa, File archivo, Element elemento) {
        mapa.computeIfAbsent(archivo, k -> new ArrayList<>())
                .add("<" + elemento.tagName() + ">: \"" + elemento.text() + "\"");
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
            e.printStackTrace();
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

    private void repararErroresComunes() {
        System.out.println("Buscando errores comunes en " + archivosXHTML.length + " archivos .xhtml...");

        Map<File, List<String>> reporteProblemas = new LinkedHashMap<>();

        Pattern patronLink = Pattern.compile("(?i)<link[^>]+(?<!/)>");
        Pattern patronHr = Pattern.compile("(?i)<hr[^>]*/?>");
        Pattern patronImg = Pattern.compile("(?i)<img(?:\"[^\"]*\"|'[^']*'|[^'\">])*?(?<!/)\\s*>");
        Pattern patronMark = Pattern.compile("(?i)<markdown[^>]+(?<!/)>");
        Pattern patronBr = Pattern.compile("(?i)<br>(?!</br>|</br>|\\s*/>)");

        // --- FASE 1: DIAGNÓSTICO ---
        for (File archivo : archivosXHTML) {
            String contenido = null;
            List<String> erroresEncontrados = new ArrayList<>();
            boolean parseoExitoso = true;
            boolean problemaDetectadoEsteArchivo = false;

            try {
                contenido = Files.readString(archivo.toPath(), StandardCharsets.UTF_8);
                try {
                    Jsoup.parse(contenido);
                } catch (Exception e) {
                    parseoExitoso = false;
                    erroresEncontrados.add("Error de sintaxis XML/HTML (posible etiqueta inválida)");
                    problemaDetectadoEsteArchivo = true;
                }

                if (contenido.contains("&nbsp;")) {
                    erroresEncontrados.add("Contiene '&nbsp;'");
                    problemaDetectadoEsteArchivo = true;
                }
                if (contenido.contains("markdown")) {
                    erroresEncontrados.add("Contiene 'markdown'");
                    problemaDetectadoEsteArchivo = true;
                }
                if (!contenido.trim().startsWith("<?xml")) {
                    erroresEncontrados.add("Falta declaración '<?xml ... ?>'");
                    problemaDetectadoEsteArchivo = true;
                }
                if (patronLink.matcher(contenido).find()) {
                    erroresEncontrados.add("Etiqueta <link> no auto-cerrada");
                    problemaDetectadoEsteArchivo = true;
                }
                if (patronHr.matcher(contenido).find()) {
                    erroresEncontrados.add("Etiqueta <hr> no auto-cerrada");
                    problemaDetectadoEsteArchivo = true;
                }
                if (patronImg.matcher(contenido).find()) {
                    erroresEncontrados.add("Etiqueta <img> no auto-cerrada");
                    problemaDetectadoEsteArchivo = true;
                }
                if (patronBr.matcher(contenido).find()) {
                    erroresEncontrados.add("Etiqueta <br> no auto-cerrada");
                    problemaDetectadoEsteArchivo = true;
                }

                // --- Añadir al mapa SI se detectó algún problema ---
                if (problemaDetectadoEsteArchivo) {
                    reporteProblemas.put(archivo, erroresEncontrados);
                }

            } catch (IOException e) {
                System.err.println("Error CRÍTICO al leer el archivo: " + archivo.getName() + " - " + e.getMessage());
                List<String> errorLectura = new ArrayList<>();
                errorLectura.add("Error de lectura del archivo");
                reporteProblemas.put(archivo, errorLectura);
            }
        }

        // --- FASE 2: REPORTE Y CONFIRMACIÓN ---
        if (reporteProblemas.isEmpty()) {
            System.out.println("\n¡Perfecto! No se encontraron archivos que necesitaran reparación.");
        } else {
            System.out.println("\n--- Reporte Detallado de Errores de Formato ---");
            reporteProblemas.forEach((archivo, listaErrores) -> {
                System.out.println(" -> " + archivo.getName() + ":");
                listaErrores.forEach(error -> System.out.println("    - " + error));
            });

            System.out.println("\nSe encontraron " + reporteProblemas.size() + " archivos con problemas.");
            System.out.print("¿Quieres intentar repararlos todos ahora? (s/n): ");
            String respuesta = this.scanner.nextLine();

            // --- FASE 3: REPARACIÓN ---
            if (respuesta.equalsIgnoreCase("s")) {
                int archivosReparados = 0;
                System.out.println("\nEmpezando la reparación...");
                for (File archivo : reporteProblemas.keySet()) {
                    try {
                        Path rutaArchivo = archivo.toPath();
                        String contenido = Files.readString(rutaArchivo, StandardCharsets.UTF_8);
                        String contenidoOriginal = contenido;

                        if (reporteProblemas.get(archivo).stream().noneMatch(e -> e.startsWith("Error de sintaxis"))) {

                            if (!contenido.trim().startsWith("<?xml")) {
                                contenido = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" + contenido;
                            }
                            contenido = contenido.replace("&nbsp;", "&#160;");
                            contenido = contenido.replaceAll("(?i)(<link[^>]+)(?<!/)>", "$1 />");
                            contenido = contenido.replaceAll("(?i)<hr[^>]*/?>", "");
                            contenido = contenido.replaceAll("(?i)(<img(?:\"[^\"]*\"|'[^']*'|[^'\">])*?)(\\s*)(?<!/)>",
                                    "$1$2/>");
                            contenido = contenido.replaceAll("(?i)<markdown[^>]*>", "");
                            contenido = contenido.replaceAll("(?i)</markdown>", "");
                            contenido = contenido.replaceAll("(?i)<markdown[^>]*/>", "");
                            contenido = contenido.replaceAll("(?i)<br>", "<br />");

                        }
                        if (!contenido.equals(contenidoOriginal) && reporteProblemas.get(archivo).stream()
                                .noneMatch(e -> e.startsWith("Error de lectura"))) {
                            Files.writeString(rutaArchivo, contenido, StandardCharsets.UTF_8);
                            System.out.println(" -> Archivo reparado: " + archivo.getName());
                            archivosReparados++;
                        } else if (reporteProblemas.get(archivo).stream()
                                .anyMatch(e -> e.startsWith("Error de sintaxis"))) {
                            System.out.println(" -> ATENCIÓN: " + archivo.getName()
                                    + " tiene errores de sintaxis graves. No se intentó reparar automáticamente. Requiere revisión manual.");
                        } else if (reporteProblemas.get(archivo).stream()
                                .anyMatch(e -> e.startsWith("Error de lectura"))) {
                            System.out.println(" -> ERROR: No se pudo reparar " + archivo.getName()
                                    + " debido a un error de lectura.");
                        } else {
                        }

                    } catch (IOException e) {
                        System.err.println(
                                "Error al intentar reparar el archivo: " + archivo.getName() + " - " + e.getMessage());
                    }
                }
                System.out.println(
                        "\nProceso de reparación finalizado. Se modificaron " + archivosReparados + " archivos.");
            } else {
                System.out.println("\nNo se realizó ninguna modificación.");
            }
        }
    }

    // --- MÉTODOS PARA EPUB ---

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
        for (nl.siegmann.epublib.domain.TOCReference tocRef : this.libro.getTableOfContents().getTocReferences()) {
            Resource recurso = tocRef.getResource();
            if (recurso == null || !recurso.getMediaType().equals(MediatypeService.XHTML))
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
            if (recurso == null || !recurso.getMediaType().equals(MediatypeService.XHTML))
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
            if (recurso == null || !recurso.getMediaType().equals(MediatypeService.XHTML))
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
                        System.out.println("  -> H1:    \"" + h1Element.text().trim() + "\"");
                        System.out.println("  -> Title: \"" + titleElement.text().trim() + "\"");
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
                coverResource.setMediaType(MediatypeService.PNG);
            } else {
                coverResource.setMediaType(MediatypeService.JPG);
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