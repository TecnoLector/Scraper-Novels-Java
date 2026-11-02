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
import java.util.Map;
import java.util.Scanner;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import javax.xml.namespace.QName;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Comparator;
import java.util.Collections;

import io.documentnode.epub4j.domain.SpineReference;
import io.documentnode.epub4j.domain.Book;
import io.documentnode.epub4j.domain.Metadata;
import io.documentnode.epub4j.domain.Resource;
import io.documentnode.epub4j.domain.Spine;
import io.documentnode.epub4j.domain.TOCReference;
import io.documentnode.epub4j.epub.EpubReader;
import io.documentnode.epub4j.epub.EpubWriter;
import io.documentnode.epub4j.domain.MediaTypes;

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
            System.out.println("4. Insertar paginas de 'Inicio de Libro'");
            System.out.println("5. Convertir un EPUB de v2 a v3 (nativo)");
            System.out.println("6. Convertir TODOS los EPUB de la carpeta a v3 (nativo)");
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
                    new DivisorEpub(scanner, rutaCarpeta).mostrarMenuDividir();
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
                case 5:
                    System.out.println("Nombre del EPUB 2 a convertir (ej: Libro.epub):");
                    String nombreArchivo = scanner.nextLine().trim();
                    Path rutaInput = Paths.get(this.rutaCarpeta, nombreArchivo);
                    String nombreSalida = nombreArchivo.toLowerCase().endsWith(".epub")
                            ? nombreArchivo.substring(0, nombreArchivo.length() - 5) + "_v3.epub"
                            : nombreArchivo + "_v3.epub";
                    Path rutaOutput = Paths.get(this.rutaCarpeta, nombreSalida);

                    if (!Files.exists(rutaInput)) {
                        System.err.println("\n❌ Error: Java no ve el archivo: " + nombreArchivo);
                        System.err.println("Ruta buscada: " + rutaInput.toAbsolutePath());

                        System.out.println("\n--- Archivos disponibles en esta carpeta ---");
                        File carpetaActual = new File(this.rutaCarpeta);
                        File[] listaArchivos = carpetaActual.listFiles((dir, name) -> name.endsWith(".epub"));

                        if (listaArchivos != null && listaArchivos.length > 0) {
                            for (File f : listaArchivos) {
                                System.out.println(" -> " + f.getName());
                            }
                            System.out.println("--------------------------------------------");
                            System.out.println("Consejo: Copia y pega el nombre EXACTO de la lista de arriba.");
                        } else {
                            System.err.println("(La carpeta parece vacía o no tiene archivos .epub)");
                        }
                        break;
                    }

                    // Conversión NATIVA (sin Calibre): genera un .epub v3 NUEVO, no toca el original.
                    try {
                        new EmpaquetadorEpub3().convertirEpub2aEpub3(rutaInput, rutaOutput);
                        System.out.println("\n✅ EPUB 3 creado: " + rutaOutput.toAbsolutePath());
                    } catch (Exception ex) {
                        System.err.println("❌ Error al convertir a EPUB 3: " + ex.getMessage());
                    }
                    break;
                case 6:
                    convertirTodosAEpub3();
                    break;
                default:
                    System.out.println("Opción no válida.");
            }
        }
    }

    /** Convierte a EPUB 3 todos los .epub de la carpeta (saltando los que ya son *_v3.epub). */
    private void convertirTodosAEpub3() {
        File carpeta = new File(this.rutaCarpeta);
        File[] epubs = carpeta.listFiles((dir, name) -> {
            String n = name.toLowerCase();
            return n.endsWith(".epub") && !n.endsWith("_v3.epub");
        });
        if (epubs == null || epubs.length == 0) {
            System.out.println("No hay archivos .epub (sin _v3) en la carpeta.");
            return;
        }
        System.out.println("Convirtiendo " + epubs.length + " EPUB a v3...");
        EmpaquetadorEpub3 emp = new EmpaquetadorEpub3();
        int ok = 0;
        int fallos = 0;
        for (File f : epubs) {
            String base = f.getName().substring(0, f.getName().length() - 5); // quita ".epub"
            Path salida = Paths.get(this.rutaCarpeta, base + "_v3.epub");
            try {
                emp.convertirEpub2aEpub3(f.toPath(), salida);
                System.out.println("  ✓ " + f.getName() + " -> " + salida.getFileName());
                ok++;
            } catch (Exception e) {
                System.err.println("  ✗ " + f.getName() + ": " + e.getMessage());
                fallos++;
            }
        }
        System.out.println("Hecho. Convertidos: " + ok + ", fallos: " + fallos);
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