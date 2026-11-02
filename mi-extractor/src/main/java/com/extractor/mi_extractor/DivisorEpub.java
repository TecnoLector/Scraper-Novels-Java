package com.extractor.mi_extractor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import io.documentnode.epub4j.domain.Book;
import io.documentnode.epub4j.domain.MediaTypes;
import io.documentnode.epub4j.domain.Metadata;
import io.documentnode.epub4j.domain.Resource;
import io.documentnode.epub4j.domain.Spine;
import io.documentnode.epub4j.domain.SpineReference;
import io.documentnode.epub4j.domain.TOCReference;
import io.documentnode.epub4j.epub.EpubReader;
import io.documentnode.epub4j.epub.EpubWriter;

/**
 * Lógica de división de EPUB (opción "Dividir") extraída de {@link UtilidadesEPUB} sin
 * cambios de comportamiento. Conserva el mismo estado: el {@link Scanner} de entrada y la
 * carpeta de trabajo.
 */
class DivisorEpub {
private static final Logger log = LoggerFactory.getLogger(DivisorEpub.class);

    private final Scanner scanner;
    private final String rutaCarpeta;

    DivisorEpub(Scanner scanner, String rutaCarpeta) {
        this.scanner = scanner;
        this.rutaCarpeta = rutaCarpeta;
    }

    void mostrarMenuDividir() {
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
            log.error("Excepción no controlada", e);
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
            System.err.println(
                    "Error al crear la subcarpeta de destino '" + nombreSubcarpeta + "': " + e.getMessage());
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
        paginaMetaResource.setMediaType(MediaTypes.XHTML);
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

    private TOCReference findTocRefByResource(
            List<TOCReference> tocReferences,
            Resource resource) {

        if (tocReferences == null || tocReferences.isEmpty()) {
            return null;
        }

        for (TOCReference tocRef : tocReferences) {

            if (resource.equals(tocRef.getResource())) {
                return tocRef; // ¡Encontrado!
            }

            TOCReference foundInChildren = findTocRefByResource(tocRef.getChildren(),
                    resource);

            if (foundInChildren != null) {
                return foundInChildren;
            }
        }

        return null;
    }
}
