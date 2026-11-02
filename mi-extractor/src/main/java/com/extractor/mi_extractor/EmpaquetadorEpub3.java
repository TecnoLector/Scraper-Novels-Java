package com.extractor.mi_extractor;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Empaquetador EPUB 3 NATIVO (sin epub4j, sin Calibre). Toma una carpeta con la estructura
 * OEBPS/{Text,Styles,Fonts,Images} y genera un .epub válido v3:
 * <ul>
 *   <li>{@code mimetype} STORED y como primera entrada,</li>
 *   <li>{@code META-INF/container.xml},</li>
 *   <li>{@code OEBPS/content.opf} con {@code <package version="3.0">}, manifest y spine,</li>
 *   <li>{@code OEBPS/nav.xhtml} (navegación EPUB 3) y {@code OEBPS/toc.ncx} (respaldo EPUB 2).</li>
 * </ul>
 * No modifica la carpeta de origen: copia sus ficheros tal cual y añade los generados al vuelo.
 */
public class EmpaquetadorEpub3 {

    private static final Logger log = LoggerFactory.getLogger(EmpaquetadorEpub3.class);

    private static final DateTimeFormatter FECHA_UTC =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC);

    /** Metadatos del libro. Mismos campos que pide el flujo de epub4j, para no confundir. */
    public static final class Metadatos {
        public String titulo = "";
        public String autor = "";
        public String sinopsis = "";
        public String generosCsv = "";
        public String idioma = "es";
        /** href relativo a OEBPS, p. ej. "Images/portada.jpg"; null si no hay portada. */
        public String coverHref = null;
    }

    /**
     * Crea el EPUB 3.
     *
     * @param carpetaRaiz        carpeta que contiene la subcarpeta OEBPS
     * @param meta               metadatos del libro
     * @param capitulosOrdenados ficheros .xhtml de OEBPS/Text en el orden deseado del spine
     * @param infoPageHtml       HTML de la página de información (o null para no incluirla)
     * @param epubSalida         ruta del .epub a escribir
     */
    public void crear(Path carpetaRaiz, Metadatos meta, List<File> capitulosOrdenados,
            String infoPageHtml, Path epubSalida) throws IOException {

        Path oebps = carpetaRaiz.resolve("OEBPS");
        if (!Files.isDirectory(oebps)) {
            throw new IOException("No se encontró la carpeta OEBPS en: " + carpetaRaiz);
        }

        String uid = "urn:uuid:" + UUID.randomUUID();
        String modificado = FECHA_UTC.format(Instant.now());
        boolean hayInfo = infoPageHtml != null && !infoPageHtml.isBlank();

        // --- 1. Escanear OEBPS (excluyendo los ficheros que regeneramos) ---
        List<String> recursos = new ArrayList<>(); // rutas relativas a OEBPS, en posix
        try (Stream<Path> paths = Files.walk(oebps)) {
            paths.filter(Files::isRegularFile).forEach(p -> {
                String rel = oebps.relativize(p).toString().replace('\\', '/');
                if (esGenerado(rel) || (hayInfo && rel.equalsIgnoreCase("Text/info_page.xhtml"))) {
                    return; // lo generamos nosotros
                }
                recursos.add(rel);
            });
        }
        recursos.sort(String::compareTo);

        // Si la portada indicada no existe entre los recursos, la omitimos (OPF válido).
        if (meta.coverHref != null) {
            boolean existe = recursos.stream().anyMatch(r -> r.equalsIgnoreCase(meta.coverHref));
            if (!existe) {
                log.warn("Portada no encontrada en OEBPS: {} (se omite)", meta.coverHref);
                meta.coverHref = null;
            }
        }

        // --- 2. Asignar ids de manifest (href -> id) ---
        Map<String, String> idPorHref = new LinkedHashMap<>();
        List<String[]> manifest = new ArrayList<>(); // {id, href, mediaType, properties|null}
        int contador = 0;
        for (String rel : recursos) {
            String id;
            String props = null;
            if (meta.coverHref != null && rel.equalsIgnoreCase(meta.coverHref)) {
                id = "cover-image";
                props = "cover-image";
            } else {
                id = "it" + (++contador);
            }
            idPorHref.put(rel, id);
            manifest.add(new String[] { id, rel, mediaType(rel), props });
        }
        if (hayInfo) {
            manifest.add(new String[] { "info", "Text/info_page.xhtml", "application/xhtml+xml", null });
        }
        manifest.add(new String[] { "nav", "nav.xhtml", "application/xhtml+xml", "nav" });
        manifest.add(new String[] { "ncx", "toc.ncx", "application/x-dtbncx+xml", null });

        // --- 3. Spine y entradas de navegación, en orden ---
        List<String> spineIds = new ArrayList<>();
        List<String[]> navEntries = new ArrayList<>(); // {href, etiqueta}
        if (hayInfo) {
            spineIds.add("info");
            navEntries.add(new String[] { "Text/info_page.xhtml", "Información del Libro" });
        }
        for (File cap : capitulosOrdenados) {
            String href = "Text/" + cap.getName();
            String id = idPorHref.get(href);
            if (id == null) {
                log.warn("Capítulo no encontrado entre los recursos de OEBPS/Text: {}", href);
                continue;
            }
            spineIds.add(id);
            navEntries.add(new String[] { href, tituloDe(cap) });
        }

        // --- 4. Construir los documentos generados ---
        byte[] container = construirContainer().getBytes(StandardCharsets.UTF_8);
        byte[] opf = construirOpf(meta, uid, modificado, manifest, spineIds).getBytes(StandardCharsets.UTF_8);
        byte[] nav = construirNav(meta, navEntries).getBytes(StandardCharsets.UTF_8);
        byte[] ncx = construirNcx(meta, uid, navEntries).getBytes(StandardCharsets.UTF_8);
        byte[] infoBytes = (infoPageHtml != null && !infoPageHtml.isBlank())
                ? infoPageHtml.getBytes(StandardCharsets.UTF_8) : null;

        // --- 5. Escribir el .epub (mimetype STORED primero) ---
        Files.createDirectories(epubSalida.getParent());
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(epubSalida.toFile()))) {
            escribirStored(zos, "mimetype", "application/epub+zip".getBytes(StandardCharsets.US_ASCII));
            escribirDeflated(zos, "META-INF/container.xml", container);
            escribirDeflated(zos, "OEBPS/content.opf", opf);
            escribirDeflated(zos, "OEBPS/nav.xhtml", nav);
            escribirDeflated(zos, "OEBPS/toc.ncx", ncx);
            if (infoBytes != null) {
                escribirDeflated(zos, "OEBPS/Text/info_page.xhtml", infoBytes);
            }
            for (String rel : recursos) {
                zos.putNextEntry(new ZipEntry("OEBPS/" + rel));
                Files.copy(oebps.resolve(rel), zos);
                zos.closeEntry();
            }
        }
        log.info("EPUB 3 creado: {}", epubSalida);
    }

    // ---------------------------------------------------------------------------------------
    //  Documentos generados
    // ---------------------------------------------------------------------------------------

    private String construirContainer() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
             + "<container version=\"1.0\" xmlns=\"urn:oasis:names:tc:opendocument:xmlns:container\">\n"
             + "  <rootfiles>\n"
             + "    <rootfile full-path=\"OEBPS/content.opf\" media-type=\"application/oebps-package+xml\"/>\n"
             + "  </rootfiles>\n"
             + "</container>\n";
    }

    private String construirOpf(Metadatos meta, String uid, String modificado,
            List<String[]> manifest, List<String> spineIds) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<package xmlns=\"http://www.idpf.org/2007/opf\" version=\"3.0\" ")
          .append("unique-identifier=\"bookid\" xml:lang=\"").append(esc(meta.idioma)).append("\">\n");
        sb.append("  <metadata xmlns:dc=\"http://purl.org/dc/elements/1.1/\">\n");
        sb.append("    <dc:identifier id=\"bookid\">").append(esc(uid)).append("</dc:identifier>\n");
        sb.append("    <dc:title>").append(esc(meta.titulo)).append("</dc:title>\n");
        sb.append("    <dc:language>").append(esc(meta.idioma)).append("</dc:language>\n");
        if (noVacio(meta.autor)) {
            sb.append("    <dc:creator>").append(esc(meta.autor)).append("</dc:creator>\n");
        }
        if (noVacio(meta.sinopsis)) {
            sb.append("    <dc:description>").append(esc(meta.sinopsis)).append("</dc:description>\n");
        }
        if (noVacio(meta.generosCsv)) {
            for (String g : meta.generosCsv.split(",")) {
                if (noVacio(g)) {
                    sb.append("    <dc:subject>").append(esc(g.trim())).append("</dc:subject>\n");
                }
            }
        }
        sb.append("    <meta property=\"dcterms:modified\">").append(modificado).append("</meta>\n");
        if (meta.coverHref != null) {
            sb.append("    <meta name=\"cover\" content=\"cover-image\"/>\n");
        }
        sb.append("  </metadata>\n");

        sb.append("  <manifest>\n");
        for (String[] it : manifest) {
            sb.append("    <item id=\"").append(it[0]).append("\" href=\"").append(esc(it[1]))
              .append("\" media-type=\"").append(it[2]).append("\"");
            if (it[3] != null) {
                sb.append(" properties=\"").append(it[3]).append("\"");
            }
            sb.append("/>\n");
        }
        sb.append("  </manifest>\n");

        sb.append("  <spine toc=\"ncx\">\n");
        for (String id : spineIds) {
            sb.append("    <itemref idref=\"").append(id).append("\"/>\n");
        }
        sb.append("  </spine>\n");
        sb.append("</package>\n");
        return sb.toString();
    }

    private String construirNav(Metadatos meta, List<String[]> navEntries) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<!DOCTYPE html>\n");
        sb.append("<html xmlns=\"http://www.w3.org/1999/xhtml\" xmlns:epub=\"http://www.idpf.org/2007/ops\" ")
          .append("xml:lang=\"").append(esc(meta.idioma)).append("\" lang=\"").append(esc(meta.idioma)).append("\">\n");
        sb.append("<head>\n  <meta charset=\"utf-8\"/>\n  <title>Navegación</title>\n</head>\n");
        sb.append("<body>\n  <nav epub:type=\"toc\" id=\"toc\">\n    <h1>Contenido</h1>\n    <ol>\n");
        for (String[] e : navEntries) {
            sb.append("      <li><a href=\"").append(esc(e[0])).append("\">").append(esc(e[1]))
              .append("</a></li>\n");
        }
        sb.append("    </ol>\n  </nav>\n</body>\n</html>\n");
        return sb.toString();
    }

    private String construirNcx(Metadatos meta, String uid, List<String[]> navEntries) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<ncx xmlns=\"http://www.daisy.org/z3986/2005/ncx/\" version=\"2005-1\">\n");
        sb.append("  <head>\n    <meta name=\"dtb:uid\" content=\"").append(esc(uid)).append("\"/>\n")
          .append("    <meta name=\"dtb:depth\" content=\"1\"/>\n")
          .append("    <meta name=\"dtb:totalPageCount\" content=\"0\"/>\n")
          .append("    <meta name=\"dtb:maxPageNumber\" content=\"0\"/>\n  </head>\n");
        sb.append("  <docTitle><text>").append(esc(meta.titulo)).append("</text></docTitle>\n");
        sb.append("  <navMap>\n");
        int orden = 1;
        for (String[] e : navEntries) {
            sb.append("    <navPoint id=\"np").append(orden).append("\" playOrder=\"").append(orden).append("\">\n")
              .append("      <navLabel><text>").append(esc(e[1])).append("</text></navLabel>\n")
              .append("      <content src=\"").append(esc(e[0])).append("\"/>\n")
              .append("    </navPoint>\n");
            orden++;
        }
        sb.append("  </navMap>\n</ncx>\n");
        return sb.toString();
    }

    // ---------------------------------------------------------------------------------------
    //  Utilidades
    // ---------------------------------------------------------------------------------------

    private static boolean esGenerado(String rel) {
        return rel.equalsIgnoreCase("content.opf")
            || rel.equalsIgnoreCase("nav.xhtml")
            || rel.equalsIgnoreCase("toc.ncx");
    }

    /** Lee el <title> del capítulo para la etiqueta del índice; si no hay, usa el nombre del fichero. */
    private static String tituloDe(File xhtml) {
        try {
            String t = Jsoup.parse(xhtml, "UTF-8").title();
            if (t != null && !t.isBlank()) {
                return t.trim();
            }
        } catch (Exception e) {
            // ignorar: usamos el nombre del fichero
        }
        String n = xhtml.getName();
        int punto = n.lastIndexOf('.');
        return (punto > 0) ? n.substring(0, punto) : n;
    }

    private static boolean noVacio(String s) {
        return s != null && !s.isBlank();
    }

    private static String mediaType(String nombre) {
        String n = nombre.toLowerCase();
        if (n.endsWith(".xhtml") || n.endsWith(".html")) return "application/xhtml+xml";
        if (n.endsWith(".css")) return "text/css";
        if (n.endsWith(".ttf")) return "font/ttf";
        if (n.endsWith(".otf")) return "font/otf";
        if (n.endsWith(".woff")) return "font/woff";
        if (n.endsWith(".woff2")) return "font/woff2";
        if (n.endsWith(".png")) return "image/png";
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return "image/jpeg";
        if (n.endsWith(".gif")) return "image/gif";
        if (n.endsWith(".svg")) return "image/svg+xml";
        if (n.endsWith(".ncx")) return "application/x-dtbncx+xml";
        if (n.endsWith(".js")) return "application/javascript";
        return "application/octet-stream";
    }

    private static String esc(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&': sb.append("&amp;"); break;
                case '<': sb.append("&lt;"); break;
                case '>': sb.append("&gt;"); break;
                case '"': sb.append("&quot;"); break;
                case '\'': sb.append("&apos;"); break;
                default: sb.append(c);
            }
        }
        return sb.toString();
    }

    private static void escribirStored(ZipOutputStream zos, String nombre, byte[] datos) throws IOException {
        ZipEntry e = new ZipEntry(nombre);
        e.setMethod(ZipEntry.STORED);
        e.setSize(datos.length);
        e.setCompressedSize(datos.length);
        CRC32 crc = new CRC32();
        crc.update(datos);
        e.setCrc(crc.getValue());
        zos.putNextEntry(e);
        zos.write(datos);
        zos.closeEntry();
    }

    private static void escribirDeflated(ZipOutputStream zos, String nombre, byte[] datos) throws IOException {
        ZipEntry e = new ZipEntry(nombre);
        e.setMethod(ZipEntry.DEFLATED);
        zos.putNextEntry(e);
        zos.write(datos);
        zos.closeEntry();
    }
}
