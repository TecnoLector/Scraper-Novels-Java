package com.extractor.mi_extractor.cli.utilidades;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.documentnode.epub4j.domain.Book;
import io.documentnode.epub4j.domain.MediaTypes;
import io.documentnode.epub4j.domain.Resource;
import io.documentnode.epub4j.domain.SpineReference;
import io.documentnode.epub4j.domain.TOCReference;
import io.documentnode.epub4j.epub.EpubReader;
import io.documentnode.epub4j.epub.EpubWriter;

/**
 * Inserción de páginas de "Inicio de Libro" en un EPUB, extraída de {@link UtilidadesEPUB}
 * sin cambios de comportamiento. Conserva el mismo estado: el {@link Scanner} y la carpeta.
 */
class InsertorPaginasLibro {
private static final Logger log = LoggerFactory.getLogger(InsertorPaginasLibro.class);

    private final Scanner scanner;
    private final String rutaCarpeta;

    InsertorPaginasLibro(Scanner scanner, String rutaCarpeta) {
        this.scanner = scanner;
        this.rutaCarpeta = rutaCarpeta;
    }

    void insertarPaginasDeLibro() {
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
            try (FileInputStream fis = new FileInputStream(rutaEpubOriginal.toFile())) {
                libroOriginal = new EpubReader().readEpub(fis);
            }
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
            res.setMediaType(MediaTypes.XHTML);
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
            log.error("Excepción no controlada", e);
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
