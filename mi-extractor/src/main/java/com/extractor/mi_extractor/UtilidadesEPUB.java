package com.extractor.mi_extractor;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.BufferedInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map; // Necesario para crearEpubDividido si copia otherProperties
import java.util.Scanner;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import javax.xml.namespace.QName; // Para metadatos personalizados (opcional)
import java.util.HashMap; // Para el mapa
import java.util.Map;
import java.util.regex.Matcher; // Para extraer número del nombre
import java.util.regex.Pattern; // Para extraer número del nombre
import nl.siegmann.epublib.domain.SpineReference;
import java.util.Comparator;

import nl.siegmann.epublib.domain.Book;
import nl.siegmann.epublib.domain.Metadata;
import nl.siegmann.epublib.domain.Resource;
import nl.siegmann.epublib.domain.Spine;
import nl.siegmann.epublib.domain.TOCReference;
import nl.siegmann.epublib.epub.EpubReader;
import nl.siegmann.epublib.epub.EpubWriter;
import nl.siegmann.epublib.service.MediatypeService;

public class UtilidadesEPUB {

    private Scanner scanner;
    private String rutaCarpeta;

    public UtilidadesEPUB(Scanner scanner) {
        this.scanner = scanner;
        System.out.println("Introduce la ruta de la carpeta donde están los archivos .epub a procesar:");
        this.rutaCarpeta = scanner.nextLine();
        File carpeta = new File(this.rutaCarpeta);
        if (!carpeta.exists() || !carpeta.isDirectory()) {
            System.err.println("Advertencia: La carpeta de trabajo especificada no existe o no es válida.");
        }
    }

    public static void iniciar(Scanner scanner) {
        UtilidadesEPUB util = new UtilidadesEPUB(scanner);
        util.mostrarMenu();
    }

    public void mostrarMenu() {
        while (true) {
            System.out.println("\n--- Utilidades de EPUB ---");
            System.out.println("Carpeta de trabajo: " + this.rutaCarpeta);
            System.out.println("1. Dividir EPUB...");
            System.out.println("2. Descomprimir un EPUB");
            System.out.println("3. Re-empaquetar carpeta como EPUB");
            System.out.println("4. Insertar páginas de 'Inicio de Libro'");
            System.out.println("0. Volver al menú principal");
            System.out.print("Elige una herramienta: ");

            int opcion;
            try {
                opcion = scanner.nextInt();
            } catch (java.util.InputMismatchException e) {
                opcion = -1;
            }
            scanner.nextLine();

            switch (opcion) {
                case 1:
                    mostrarMenuDividir();
                    break;
                case 2:
                    descomprimirEpub();
                    break;
                case 3:
                    reempaquetarComoEpub();
                    break;
                case 4:
                    insertarPaginasDeLibro();
                    break;
                case 0:
                    return;
                default:
                    System.out.println("Opción no válida.");
            }
        }
    }

    // ---Submenú para Dividir EPUB ---
    private void mostrarMenuDividir() {
        System.out.println("\n--- Dividir EPUB ---");
        System.out.println("Introduce el nombre del archivo EPUB a dividir (ej: MiLibro.epub):");
        String nombreEpubOriginal = scanner.nextLine();
        Path rutaEpubOriginal = Paths.get(rutaCarpeta, nombreEpubOriginal);

        if (!Files.exists(rutaEpubOriginal)) {
            System.err.println("Error: El archivo '" + nombreEpubOriginal + "' no se encuentra en la carpeta.");
            return;
        }

        String nombreBase = nombreEpubOriginal;
        if (nombreBase.toLowerCase().endsWith(".epub")) {
            nombreBase = nombreBase.substring(0, nombreBase.length() - 5);
        }

        System.out.println("(Opcional) Introduce el sitio de extracción (ej: skynovels.net):");
        String sitioExtraccion = scanner.nextLine();
        System.out.println("(Opcional) Introduce tu nombre o alias como creador del archivo:");
        String creadorArchivo = scanner.nextLine();

        System.out.println("\n¿Cómo quieres dividir el EPUB?");
        System.out.println("1. Por rango específico (ej: del capítulo 50 al 100) -> 1 archivo nuevo.");
        System.out.println("2. En N partes iguales (aprox.) -> N archivos nuevos.");
        System.out.println("3. En trozos de N capítulos -> Varios archivos nuevos.");
        System.out.println("4. Por número de capítulos personalizado por libro");
        System.out.println("0. Cancelar");
        System.out.print("Elige una opción: ");

        int opcionSplit;
        try {
            opcionSplit = scanner.nextInt();
        } catch (java.util.InputMismatchException e) {
            opcionSplit = -1;
        }
        scanner.nextLine();

        Book libroOriginal = null;
        try {
            System.out.println("Cargando EPUB original...");
            libroOriginal = new EpubReader().readEpub(new FileInputStream(rutaEpubOriginal.toFile()));
            if (libroOriginal.getSpine().getSpineReferences() == null
                    || libroOriginal.getSpine().getSpineReferences().isEmpty()) {
                System.err.println("Error: El EPUB original parece no tener contenido legible (spine vacío).");
                return;
            }
            System.out.println(
                    "EPUB cargado. Total secciones en orden de lectura (spine): " + libroOriginal.getSpine().size());
        } catch (IOException e) {
            System.err.println("Error fatal al cargar el EPUB original: " + e.getMessage());
            return;
        }

        switch (opcionSplit) {
            case 1:
                dividirPorRango(libroOriginal, nombreEpubOriginal, sitioExtraccion, creadorArchivo);
                break;
            case 2:
                dividirEnNPartes(libroOriginal, nombreEpubOriginal, sitioExtraccion, creadorArchivo);
                break;
            case 3:
                dividirPorTamanoChunk(libroOriginal, nombreEpubOriginal, sitioExtraccion, creadorArchivo);
                break;
            case 4:
                dividirPorCapitulosPersonalizados(libroOriginal, nombreEpubOriginal, sitioExtraccion, creadorArchivo);
                break;
            case 0:
                System.out.println("División cancelada.");
                break;
            default:
                System.out.println("Opción no válida.");
        }
    }

    // --- Opción 1 - Dividir por Rango ---
    private void dividirPorRango(Book libroOriginal, String nombreBase, String sitioExtraccion, String creadorArchivo) {
        System.out.print("¿Desde qué número de capítulo/sección quieres empezar?: ");
        int inicio;
        try {
            inicio = scanner.nextInt();
        } catch (Exception e) {
            System.err.println("Número inválido.");
            scanner.nextLine();
            return;
        }
        System.out.print("¿Hasta qué número de capítulo/sección quieres terminar?: ");
        int fin;
        try {
            fin = scanner.nextInt();
        } catch (Exception e) {
            System.err.println("Número inválido.");
            scanner.nextLine();
            return;
        }
        scanner.nextLine();

        int totalSecciones = libroOriginal.getSpine().size();
        if (inicio < 1 || fin > totalSecciones || inicio > fin) {
            System.err.println("Error: Rango inválido. El libro tiene de 1 a " + totalSecciones
                    + " secciones en orden de lectura.");
            return;
        }

        int startIndex = inicio - 1;
        int endIndex = fin - 1;

        String nombreSubcarpeta = nombreBase + "_Rango_" + inicio + "-" + fin;
        Path rutaSubcarpeta = Paths.get(rutaCarpeta, nombreSubcarpeta);
        try {
            Files.createDirectories(rutaSubcarpeta);
        } catch (IOException e) {
            System.err.println("Error al crear la subcarpeta de destino: " + e.getMessage());
            return;
        }

        String nombreNuevo = nombreBase + "_Cap" + inicio + "-" + fin + ".epub";
        Path rutaNuevo = rutaSubcarpeta.resolve(nombreNuevo);

        System.out.println("Creando EPUB para el rango " + inicio + " - " + fin + "...");
        try {
            crearEpubDividido(libroOriginal, startIndex, endIndex, 1, 1, rutaNuevo, sitioExtraccion, creadorArchivo);
            System.out.println("¡Éxito! Se ha creado el archivo: " + nombreNuevo);
        } catch (IOException e) {
            System.err.println("Error al crear el EPUB dividido: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // --- Opción 2 - Dividir en N Partes ---
    private void dividirEnNPartes(Book libroOriginal, String nombreBase, String sitioExtraccion,
            String creadorArchivo) {
        System.out.print("¿En cuántas partes quieres dividir el libro?: ");
        int numPartes;
        try {
            numPartes = scanner.nextInt();
        } catch (Exception e) {
            System.err.println("Número inválido.");
            scanner.nextLine();
            return;
        }
        scanner.nextLine();

        int totalSecciones = libroOriginal.getSpine().size();
        if (numPartes < 1 || numPartes > totalSecciones) {
            System.err.println("Error: Número de partes inválido (debe ser entre 1 y " + totalSecciones + ").");
            return;
        }

        int seccionesPorParteBase = (int) Math.ceil((double) totalSecciones / numPartes);

        String nombreSubcarpeta = nombreBase + "_Dividido_en_" + numPartes + "_partes";
        Path rutaSubcarpeta = Paths.get(rutaCarpeta, nombreSubcarpeta);
        try {
            Files.createDirectories(rutaSubcarpeta);
        } catch (IOException e) {
            System.err.println("Error al crear subcarpeta: " + e.getMessage());
            return;
        }

        System.out.println(
                "Dividiendo en " + numPartes + " partes (aprox. " + seccionesPorParteBase + " secciones por parte)...");

        int indiceActual = 0;
        for (int i = 1; i <= numPartes; i++) {
            int startIndex = indiceActual;
            int endIndex = Math.min(indiceActual + seccionesPorParteBase - 1, totalSecciones - 1);

            String nombreParte = nombreBase.replace(".epub", "") + "_Parte" + String.format("%02d", i) + ".epub";
            Path rutaParte = Paths.get(rutaCarpeta, nombreParte);

            System.out.println(
                    "Creando Parte " + i + " (Secciones " + (startIndex + 1) + " - " + (endIndex + 1) + ")...");
            try {
                crearEpubDividido(libroOriginal, startIndex, endIndex, i, numPartes, rutaParte, sitioExtraccion,
                        creadorArchivo);
                System.out.println(" -> Archivo creado: " + nombreParte);
            } catch (IOException e) {
                System.err.println("Error al crear la Parte " + i + ": " + e.getMessage());
            }
            indiceActual = endIndex + 1;
            if (indiceActual >= totalSecciones && i < numPartes) {
                System.out.println(
                        "Advertencia: Se completaron todas las secciones antes de llegar al número de partes deseado.");
                break;
            }
        }
        System.out.println("Proceso de división en " + numPartes + " partes completado.");
    }

    // --- Opción 3 - Dividir por Tamaño de Chunk ---
    private void dividirPorTamanoChunk(Book libroOriginal, String nombreBase, String sitioExtraccion,
            String creadorArchivo) {
        System.out.print("¿Cuántos capítulos/secciones quieres por cada archivo EPUB?: ");
        int chunkSize;
        try {
            chunkSize = scanner.nextInt();
        } catch (Exception e) {
            System.err.println("Número inválido.");
            scanner.nextLine();
            return;
        }
        scanner.nextLine();

        int totalSecciones = libroOriginal.getSpine().size();
        if (chunkSize < 1) {
            System.err.println("Error: El tamaño del trozo debe ser al menos 1.");
            return;
        }

        int numArchivos = (int) Math.ceil((double) totalSecciones / chunkSize);

        String nombreSubcarpeta = nombreBase + "_Trozos_de_" + chunkSize;
        Path rutaSubcarpeta = Paths.get(rutaCarpeta, nombreSubcarpeta);
        try {
            Files.createDirectories(rutaSubcarpeta);
        } catch (IOException e) {
            System.err.println("Error al crear subcarpeta: " + e.getMessage());
            return;
        }
        System.out.println(
                "Dividiendo en " + numArchivos + " archivos (máx. " + chunkSize + " secciones por archivo)...");

        int indiceActual = 0;
        for (int i = 1; i <= numArchivos; i++) {
            int startIndex = indiceActual;
            int endIndex = Math.min(indiceActual + chunkSize - 1, totalSecciones - 1);

            String nombreParte = nombreBase.replace(".epub", "") + "_Chunk" + String.format("%02d", i) + ".epub";
            Path rutaParte = Paths.get(rutaCarpeta, nombreParte);

            System.out.println(
                    "Creando Archivo " + i + " (Secciones " + (startIndex + 1) + " - " + (endIndex + 1) + ")...");
            try {
                crearEpubDividido(libroOriginal, startIndex, endIndex, i, numArchivos, rutaParte, sitioExtraccion,
                        creadorArchivo);
                System.out.println(" -> Archivo creado: " + nombreParte);
            } catch (IOException e) {
                System.err.println("Error al crear el Archivo " + i + ": " + e.getMessage());
            }
            indiceActual = endIndex + 1;
            if (indiceActual >= totalSecciones)
                break;
        }
        System.out.println("Proceso de división en trozos completado.");
    }

    /**
     * Opción 4 Modificada: Divide el EPUB preguntando HASTA qué número de capítulo
     * (basado en el nombre de archivo) va cada libro, guardando en subcarpeta.
     */
    private void dividirPorCapitulosPersonalizados(Book libroOriginal, String nombreBase,
            String sitioExtraccion, String creadorArchivo) {

        List<SpineReference> spineRefs = libroOriginal.getSpine().getSpineReferences();
        int totalSecciones = spineRefs.size();

        Map<Integer, Integer> mapaCapituloAIndice = new HashMap<>();
        Pattern patronNumeroArchivo = Pattern.compile("(?i)(?:Capitulo|Cap|Chapter|Ch)(\\d+)\\.xhtml");
        System.out.println("Mapeando números de capítulo desde nombres de archivo...");
        int maxCapituloEncontrado = 0;
        for (int i = 0; i < totalSecciones; i++) {
            Resource res = spineRefs.get(i).getResource();
            if (res != null && res.getHref() != null) {
                Matcher matcher = patronNumeroArchivo.matcher(res.getHref());
                if (matcher.find()) {
                    try {
                        int numeroCap = Integer.parseInt(matcher.group(1));
                        mapaCapituloAIndice.put(numeroCap, i);
                        if (numeroCap > maxCapituloEncontrado) {
                            maxCapituloEncontrado = numeroCap;
                        }
                    } catch (NumberFormatException e) {
                    }
                } else {
                }
            }
        }
        if (mapaCapituloAIndice.isEmpty()) {
            return;
        }
        System.out.println("Mapeo completado. Capítulo más alto encontrado: " + maxCapituloEncontrado);

        System.out.print("\n¿Cuántos libros EPUB nuevos quieres crear?: ");
        int numLibros = 0;
        try {
            numLibros = scanner.nextInt();
        } catch (Exception e) {
            System.err.println("Número inválido.");
            scanner.nextLine();
        }
        scanner.nextLine();

        if (numLibros < 1) {
            System.err.println("Debes crear al menos 1 libro.");
            return;
        }

        String nombreSubcarpeta = nombreBase + "_Division_Personalizada_Num";
        Path rutaSubcarpeta = Paths.get(rutaCarpeta, nombreSubcarpeta);
        try {
            Files.createDirectories(rutaSubcarpeta);
            System.out.println("Los libros se guardarán en la subcarpeta: " + nombreSubcarpeta);
        } catch (IOException e) {
            System.err.println("Error al crear la subcarpeta de destino '" + nombreSubcarpeta + "': " + e.getMessage());
            return;
        }

        System.out.println("Okay, definiremos los rangos para " + numLibros + " libros usando números de capítulo.");
        int indiceActualSpine = 0;
        int ultimoCapituloProcesado = 0;

        for (int i = 1; i <= numLibros; i++) {
            System.out.println("\n--- Libro #" + i + " de " + numLibros + " ---");
            int capituloInicio = ultimoCapituloProcesado + 1;

            int startIndexSpine = -1;
            int primerCapituloDisponible = -1;
            for (int capNum = capituloInicio; capNum <= maxCapituloEncontrado; capNum++) {
                if (mapaCapituloAIndice.containsKey(capNum)) {
                    startIndexSpine = mapaCapituloAIndice.get(capNum);
                    primerCapituloDisponible = capNum;
                    break;
                }
            }
            if (startIndexSpine == -1) {
                break;
            }
            capituloInicio = primerCapituloDisponible;
            System.out.println("Este libro comenzará en el capítulo #" + capituloInicio + " (índice spine: "
                    + startIndexSpine + ").");

            System.out.print("¿HASTA qué número de capítulo debe llegar este libro? (Entre "
                    + capituloInicio + " y " + maxCapituloEncontrado + "): ");
            int capituloFinalParaEsteLibro;
            try {
                capituloFinalParaEsteLibro = scanner.nextInt();
            } catch (Exception e) {
                i--;
                continue;
            }
            scanner.nextLine();

            if (capituloFinalParaEsteLibro < capituloInicio) {
                i--;
                continue;
            }
            int endIndexSpine = -1;
            int ultimoCapituloIncluido = -1;
            for (int capNum = capituloFinalParaEsteLibro; capNum >= capituloInicio; capNum--) {
                if (mapaCapituloAIndice.containsKey(capNum)) {
                    endIndexSpine = mapaCapituloAIndice.get(capNum);
                    ultimoCapituloIncluido = capNum;
                    break;
                }
            }
            if (endIndexSpine == -1) {
                i--;
                continue;
            }

            String nombreParte = nombreBase + "_Libro" + String.format("%02d", i) + "_Cap" + capituloInicio + "-"
                    + ultimoCapituloIncluido + ".epub";
            Path rutaParte = rutaSubcarpeta.resolve(nombreParte);

            System.out.println(
                    "Creando Libro " + i + " (Índices spine " + startIndexSpine + " a " + endIndexSpine + " -> Caps "
                            + capituloInicio + "-" + ultimoCapituloIncluido + ") en archivo '" + nombreParte + "'...");
            try {
                crearEpubDividido(libroOriginal, startIndexSpine, endIndexSpine, i, numLibros, rutaParte,
                        sitioExtraccion, creadorArchivo);
                System.out.println(" -> Archivo creado con éxito.");
            } catch (IOException e) {
                System.err.println("   *** Error al crear el Libro " + i + ": " + e.getMessage() + " ***");
            }

            ultimoCapituloProcesado = ultimoCapituloIncluido;
            indiceActualSpine = endIndexSpine + 1;

        }

        System.out.println("\nProceso de división personalizada completado.");
        if (indiceActualSpine < totalSecciones) {
        }

    }

    private void crearEpubDividido(Book libroOriginal, int startIndex, int endIndex,
            int partNum, int totalParts, Path rutaDestino,
            String sitioExtraccion, String creadorArchivo) throws IOException {

        Book libroDividido = new Book();

        libroDividido.setMetadata(new Metadata());
        libroDividido.getMetadata().setAuthors(libroOriginal.getMetadata().getAuthors());
        libroDividido.getMetadata().setIdentifiers(libroOriginal.getMetadata().getIdentifiers());
        libroDividido.getMetadata().setLanguage(libroOriginal.getMetadata().getLanguage());
        libroDividido.getMetadata().setSubjects(libroOriginal.getMetadata().getSubjects()); // Géneros
        libroDividido.getMetadata().setDescriptions(libroOriginal.getMetadata().getDescriptions()); // Sinopsis

        String tituloOriginal = libroOriginal.getTitle();
        if (tituloOriginal == null || tituloOriginal.isEmpty())
            tituloOriginal = "Libro sin Título";
        String nuevoTitulo = tituloOriginal + " (Parte " + partNum + " de " + totalParts + ")";
        libroDividido.getMetadata().addTitle(nuevoTitulo);

        Resource coverImageResource = libroOriginal.getCoverImage();
        if (coverImageResource != null) {
            byte[] coverData;
            try (InputStream is = coverImageResource.getInputStream()) {
                coverData = is.readAllBytes();
            }
            Resource newCoverResource = new Resource(coverData, coverImageResource.getHref());
            newCoverResource.setMediaType(coverImageResource.getMediaType());
            // Añadir el recurso al nuevo libro
            libroDividido.getResources().add(newCoverResource);
            // Establecerlo como portada
            libroDividido.setCoverImage(newCoverResource);
            System.out.println(" -> Portada copiada.");
        } else {
            System.out.println(" -> Advertencia: EPUB original sin portada definida.");
        }

        Spine spineOriginal = libroOriginal.getSpine();
        for (Resource res : libroOriginal.getResources().getAll()) {
            boolean esContenido = spineOriginal.getResourceIndex(res) >= 0;
            boolean esPortada = res.equals(coverImageResource);

            if (!esContenido && !esPortada) {
                try (InputStream is = res.getInputStream()) {
                    byte[] data = is.readAllBytes();
                    Resource newRes = new Resource(data, res.getHref());
                    newRes.setMediaType(res.getMediaType());
                    libroDividido.getResources().add(newRes);
                    System.out.println(" -> Recurso copiado: " + res.getHref());
                } catch (Exception e) {
                    System.err.println(
                            "Advertencia: No se pudo copiar el recurso " + res.getHref() + ": " + e.getMessage());
                }
            }
        }

        int primerCapitulo = startIndex + 1;
        int ultimoCapitulo = endIndex + 1;
        String paginaMetaHtml = generarPaginaMetadatosXHTML(libroOriginal.getMetadata(), primerCapitulo, ultimoCapitulo,
                sitioExtraccion, creadorArchivo);
        Resource paginaMetaResource = new Resource(paginaMetaHtml.getBytes(StandardCharsets.UTF_8),
                "OEBPS/Text/metadata_page.xhtml");
        paginaMetaResource.setMediaType(MediatypeService.XHTML);
        libroDividido.getResources().add(paginaMetaResource);
        libroDividido.addSection("Información del Libro", paginaMetaResource);
        System.out.println(" -> Página de metadatos generada.");

        List<Resource> spineResourcesOriginal = libroOriginal.getSpine().getSpineReferences().stream()
                .map(ref -> ref.getResource())
                .collect(Collectors.toList());

        for (int i = startIndex; i <= endIndex; i++) {
            if (i < spineResourcesOriginal.size()) {
                Resource contentResourceOriginal = spineResourcesOriginal.get(i);

                TOCReference tocRef = findTocRefByResource(libroOriginal.getTableOfContents().getTocReferences(),
                        contentResourceOriginal);
                String tituloSeccion = (tocRef != null) ? tocRef.getTitle() : contentResourceOriginal.getHref();

                byte[] contentData;
                try (InputStream is = contentResourceOriginal.getInputStream()) {
                    contentData = is.readAllBytes();
                }
                Resource newContentResource = new Resource(contentData, contentResourceOriginal.getHref());
                newContentResource.setMediaType(contentResourceOriginal.getMediaType());

                libroDividido.getResources().add(newContentResource);
                libroDividido.addSection(tituloSeccion, newContentResource);

            } else {
                System.err.println("Advertencia: Índice fuera de rango al intentar añadir sección " + (i + 1));
            }
        }
        System.out.println(" -> Añadidas " + (endIndex - startIndex + 1) + " secciones de contenido.");

        EpubWriter epubWriter = new EpubWriter();
        epubWriter.write(libroDividido, new FileOutputStream(rutaDestino.toFile()));
    }

    // --- Helper para generar la Página de Metadatos ---
    private String generarPaginaMetadatosXHTML(Metadata metadataOriginal, int startChapNum, int endChapNum,
            String sitioExtraccion, String creadorArchivo) {
        StringBuilder sb = new StringBuilder();
        String title = metadataOriginal.getFirstTitle();
        String author = (metadataOriginal.getAuthors() != null && !metadataOriginal.getAuthors().isEmpty())
                ? metadataOriginal.getAuthors().get(0).getFirstname() + " "
                        + metadataOriginal.getAuthors().get(0).getLastname()
                : "Desconocido";
        String synopsis = (metadataOriginal.getDescriptions() != null && !metadataOriginal.getDescriptions().isEmpty())
                ? metadataOriginal.getDescriptions().get(0)
                : "No disponible.";
        String genres = (metadataOriginal.getSubjects() != null && !metadataOriginal.getSubjects().isEmpty())
                ? String.join(", ", metadataOriginal.getSubjects())
                : "No especificados.";

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
                .append("    <dt>Nombre del Libro #:</dt>\n")
                .append("    <dt>Capítulos Incluidos:</dt>\n")
                .append("    <dd>").append(startChapNum).append(" - ").append(endChapNum).append("</dd>\n")
                .append("  </dl>\n")
                .append("  <h2>Información General</h2>\n")
                .append("  <dl>\n")
                .append("    <dt>Título Original:</dt>\n")
                .append("    <dd>").append(title != null ? title : "No disponible").append("</dd>\n")
                .append("    <dt>Autor:</dt>\n")
                .append("    <dd>").append(author).append("</dd>\n")
                .append("    <dt>Géneros:</dt>\n")
                .append("    <dd>").append(genres).append("</dd>\n")
                .append("    <dt>Sinopsis:</dt>\n")
                .append("    <dd>").append(synopsis).append("</dd>\n");

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

    private nl.siegmann.epublib.domain.TOCReference findTocRefByResource(
            List<nl.siegmann.epublib.domain.TOCReference> tocReferences,
            nl.siegmann.epublib.domain.Resource resource) {

        if (tocReferences == null || tocReferences.isEmpty()) {
            return null;
        }

        for (nl.siegmann.epublib.domain.TOCReference tocRef : tocReferences) {

            if (resource.equals(tocRef.getResource())) {
                return tocRef; // ¡Encontrado!
            }

            nl.siegmann.epublib.domain.TOCReference foundInChildren = findTocRefByResource(tocRef.getChildren(),
                    resource);

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

        System.out.println("Introduce el nombre del nuevo archivo EPUB de salida (ej: MiLibro_final.epub):");
        String nombreEpubSalida = scanner.nextLine();
        Path rutaEpubSalida = Paths.get(rutaCarpeta, nombreEpubSalida);

        try (
                FileOutputStream fos = new FileOutputStream(rutaEpubSalida.toFile());
                ZipOutputStream zos = new ZipOutputStream(fos)) {
            System.out.println("Empaquetando...");

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

            try (Stream<Path> paths = Files.walk(rutaCarpetaFuente)) {

                paths.filter(Files::isRegularFile)
                        .filter(path -> !path.getFileName().toString().equals("mimetype"))
                        .forEach(path -> {
                            try {
                                // Creamos el nombre de la ruta dentro del ZIP
                                // ej: "OEBPS/Text/Capitulo0001.xhtml"
                                String zipPath = rutaCarpetaFuente.relativize(path).toString().replace(File.separator,
                                        "/");

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

    private void insertarPaginasDeLibro() {
        System.out.println("\n--- Insertar Páginas de Inicio de Libro ---");
        System.out.println("Introduce el nombre del archivo EPUB a modificar (ej: MiLibro.epub):");
        String nombreEpubOriginal = scanner.nextLine();
        Path rutaEpubOriginal = Paths.get(rutaCarpeta, nombreEpubOriginal);

        if (!Files.exists(rutaEpubOriginal)) {
            System.err.println("Error: El archivo '" + nombreEpubOriginal + "' no se encuentra.");
            return;
        }

        String nombreBase = nombreEpubOriginal;
        if (nombreBase.toLowerCase().endsWith(".epub")) {
            nombreBase = nombreBase.substring(0, nombreBase.length() - 5);
        }

        Book libroOriginal = null;
        try {
            System.out.println("Cargando EPUB original...");
            libroOriginal = new EpubReader().readEpub(new FileInputStream(rutaEpubOriginal.toFile()));
        } catch (IOException e) {
            System.err.println("Error fatal al cargar el EPUB: " + e.getMessage());
            return;
        }

        // --- 1. Mapear Capítulos (igual que en Opción 4) ---
        List<SpineReference> spineRefs = libroOriginal.getSpine().getSpineReferences();
        int totalSecciones = spineRefs.size();
        Map<Integer, Integer> mapaCapituloAIndice = new HashMap<>();
        Pattern patronNumeroArchivo = Pattern.compile("(?i)(?:Capitulo|Cap|Chapter|Ch)(\\d+)\\.xhtml");
        System.out.println("Mapeando números de capítulo desde nombres de archivo...");
        int maxCapituloEncontrado = 0;
        for (int i = 0; i < totalSecciones; i++) {
            Resource res = spineRefs.get(i).getResource();
            if (res != null && res.getHref() != null) {
                Matcher matcher = patronNumeroArchivo.matcher(res.getHref());
                if (matcher.find()) {
                    try {
                        int numeroCap = Integer.parseInt(matcher.group(1));
                        mapaCapituloAIndice.put(numeroCap, i);
                        if (numeroCap > maxCapituloEncontrado)
                            maxCapituloEncontrado = numeroCap;
                    } catch (NumberFormatException e) {
                        /* Ignorar */ }
                }
            }
        }
        if (mapaCapituloAIndice.isEmpty()) {
            System.err.println("Error: No se pudo encontrar ningún archivo de capítulo con nombre estándar.");
            return;
        }
        System.out.println("Mapeo completado. Capítulos encontrados del 1 al " + maxCapituloEncontrado);

        // --- 2. Pedir Información Común UNA SOLA VEZ ---
        System.out.println("\nIntroduce el Sitio de Extracción (se usará en todas las páginas):");
        String sitioExtraccion = scanner.nextLine();
        String creadorArchivo = "TecnoLector";

        System.out.print("¿Cuántas páginas de 'Inicio de Libro' quieres insertar?: ");
        int numPaginas;
        try {
            numPaginas = scanner.nextInt();
        } catch (Exception e) {
            System.err.println("Número inválido.");
            scanner.nextLine();
            return;
        }
        scanner.nextLine();

        if (numPaginas < 1) {
            System.err.println("Debes insertar al menos 1 página.");
            return;
        }

        List<PaginaInsertar> listaPaginas = new ArrayList<>();

        // --- 3. Definir todas las páginas y sus ubicaciones ---
        System.out.println("Define las " + numPaginas + " páginas:");
        for (int i = 1; i <= numPaginas; i++) {
            System.out.println("\n--- Página #" + i + " ---");
            System.out.print("Nombre para esta página (ej: Libro 2: El Despertar): ");
            String nombreLibro = scanner.nextLine();

            System.out.print("¿Insertar DESPUÉS del capítulo número? (ej: 67): ");
            int numCapAnterior;
            try {
                numCapAnterior = scanner.nextInt();
            } catch (Exception e) {
                System.err.println("Número inválido.");
                scanner.nextLine();
                i--;
                continue;
            }
            scanner.nextLine(); // Limpiar buffer

            if (!mapaCapituloAIndice.containsKey(numCapAnterior)) {
                System.err.println("Error: No se encontró el capítulo #" + numCapAnterior + " en el EPUB.");
                System.err.println("Esta página será omitida. Intenta de nuevo.");
                i--;
                continue;
            }

            int indiceEncontrado = mapaCapituloAIndice.get(numCapAnterior);
            String hrefEncontrado = spineRefs.get(indiceEncontrado).getResource().getHref();
            System.out.println(
                    "OK. Se insertará después de: " + hrefEncontrado + " (Índice spine: " + indiceEncontrado + ")");

            PaginaInsertar pagina = new PaginaInsertar(nombreLibro, numCapAnterior);
            pagina.indiceSpineEncontrado = indiceEncontrado;
            listaPaginas.add(pagina);
        }

        // --- 4. Ordenar e Insertar las páginas en el libro (en memoria) ---
        System.out.println("\nProcesando e insertando " + listaPaginas.size() + " páginas...");
        int insercionesHechas = 0;
        listaPaginas.sort(Comparator.comparingInt(p -> p.indiceSpineEncontrado));

        for (PaginaInsertar pagina : listaPaginas) {
            String htmlPagina = generarPaginaTituloLibroXHTML(pagina.nombreLibro, sitioExtraccion, creadorArchivo,
                    (insercionesHechas + 1));

            String hrefPagina = "OEBPS/Text/info_libro_" + (insercionesHechas + 1) + ".xhtml";
            Resource res = new Resource(htmlPagina.getBytes(StandardCharsets.UTF_8), hrefPagina);
            res.setMediaType(MediatypeService.XHTML);
            libroOriginal.getResources().add(res); 

            int indiceRealInsercion = pagina.indiceSpineEncontrado + 1 + insercionesHechas;
            libroOriginal.getSpine().getSpineReferences().add(indiceRealInsercion, new SpineReference(res));
            TOCReference tocRef = new TOCReference(pagina.nombreLibro, res);
            libroOriginal.getTableOfContents().getTocReferences().add(indiceRealInsercion, tocRef);

            System.out.println("Insertado '" + pagina.nombreLibro + "' después del capítulo "
                    + pagina.numeroCapituloAnterior + ".");
            insercionesHechas++;
        }

        // --- 5. Guardar el nuevo EPUB ---
        System.out.println("\nIntroduce el nombre del archivo EPUB final (ej: " + nombreBase + "_modificado.epub):");
        String nombreEpubNuevo = scanner.nextLine();
        if (nombreEpubNuevo.isBlank())
            nombreEpubNuevo = nombreEpubOriginal.replace(".epub", "_con_separadores.epub");
        Path rutaEpubNuevo = Paths.get(rutaCarpeta, nombreEpubNuevo);

        try {
            EpubWriter epubWriter = new EpubWriter();
            epubWriter.write(libroOriginal, new FileOutputStream(rutaEpubNuevo.toFile()));
            System.out.println("\n¡Éxito! Se ha creado el archivo modificado: " + nombreEpubNuevo);
        } catch (IOException e) {
            System.err.println("Error al guardar el EPUB modificado: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Ayudante para generar el HTML de una página de "Inicio de Libro"
     */
    private String generarPaginaTituloLibroXHTML(String nombreLibro, String sitioExtraccion, String creadorArchivo,
            int numLibro) {
        StringBuilder sb = new StringBuilder();

        String style = "body { font-family: sans-serif; margin: 2em; text-align: center; } " +
                "h1 { font-size: 2.5em; margin-top: 3em; } " +
                "h2 { font-size: 1.8em; font-weight: normal; margin-top: 1em; } " +
                "dl { margin-top: 4em; display: inline-block; text-align: left; } " +
                "dt { font-weight: bold; } " +
                "dd { margin-left: 0; margin-bottom: 1em; }";

        sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
                .append("<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.1//EN\"\n")
                .append("  \"http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd\">\n")
                .append("<html xmlns=\"http://www.w3.org/1999/xhtml\" xml:lang=\"es\">\n")
                .append("<head>\n")
                .append("  <title>").append(nombreLibro).append("</title>\n")
                .append("  <link rel=\"stylesheet\" type=\"text/css\" href=\"../Styles/stylesheet.css\" />\n")
                .append("  <style type=\"text/css\">").append(style).append("</style>\n")
                .append("</head>\n")
                .append("<body>\n")
                .append("  <h1>Libro ").append(numLibro).append("</h1>\n")
                .append("  <h2>").append(nombreLibro).append("</h2>\n")
                .append("  <hr/>\n")
                .append("  <dl>\n");

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

    private static class PaginaInsertar {
        String nombreLibro;
        int numeroCapituloAnterior;
        int indiceSpineEncontrado = -1;

        public PaginaInsertar(String nombreLibro, int numeroCapituloAnterior) {
            this.nombreLibro = nombreLibro;
            this.numeroCapituloAnterior = numeroCapituloAnterior;
        }
    }
}