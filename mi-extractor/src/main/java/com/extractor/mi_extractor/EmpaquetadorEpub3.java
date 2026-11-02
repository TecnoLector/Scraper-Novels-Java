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
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
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

    // ---------------------------------------------------------------------------------------
    //  Conversión EPUB 2 -> EPUB 3 (genera un fichero NUEVO; no toca el original)
    // ---------------------------------------------------------------------------------------

    /**
     * Convierte un EPUB 2 a EPUB 3: sube el OPF a 3.0, añade {@code dcterms:modified} y
     * {@code dc:identifier} si faltan, genera {@code nav.xhtml} (desde el {@code toc.ncx} o, si no
     * hay, desde el spine) y lo añade al manifest con {@code properties="nav"}, conserva el ncx y
     * reempaqueta con {@code mimetype} STORED primero.
     */
    public void convertirEpub2aEpub3(Path entrada, Path salida) throws IOException {
        // 1. Cargar todas las entradas en memoria.
        Map<String, byte[]> entradas = new LinkedHashMap<>();
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(entrada))) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                if (!e.isDirectory()) {
                    entradas.put(e.getName(), zis.readAllBytes());
                }
            }
        }

        // 2. Localizar el OPF vía META-INF/container.xml.
        byte[] containerBytes = entradas.get("META-INF/container.xml");
        if (containerBytes == null) {
            throw new IOException("No es un EPUB válido: falta META-INF/container.xml");
        }
        String opfPath = rutaRootfile(new String(containerBytes, StandardCharsets.UTF_8));
        if (opfPath == null) {
            throw new IOException("container.xml no indica el OPF (rootfile full-path).");
        }
        byte[] opfBytes = entradas.get(opfPath);
        if (opfBytes == null) {
            throw new IOException("No se encontró el OPF indicado en container.xml: " + opfPath);
        }
        String opfDir = opfPath.contains("/") ? opfPath.substring(0, opfPath.lastIndexOf('/') + 1) : "";

        // 3. Parsear y actualizar el OPF. Tolerante a prefijos de namespace: epub4j escribe
        //    <opf:package>/<opf:manifest>/..., mientras que Sigil suele usarlos sin prefijo.
        Document opf = Jsoup.parse(new String(opfBytes, StandardCharsets.UTF_8), "", Parser.xmlParser());
        Element pkg = primero(opf, "package");
        if (pkg == null) {
            throw new IOException("OPF inválido: no se encontró <package>.");
        }
        String prefijo = prefijoDe(pkg); // "opf:" o ""
        Element metadata = primero(opf, "metadata");
        Element manifest = primero(opf, "manifest");
        Element spine = primero(opf, "spine");
        if (metadata == null || manifest == null || spine == null) {
            throw new IOException("OPF incompleto (falta metadata/manifest/spine).");
        }

        pkg.attr("version", "3.0");
        asegurarIdentificador(opf, pkg, metadata);

        // dcterms:modified (requerido en EPUB 3), con el mismo prefijo que el resto del OPF.
        boolean tieneModified = todos(opf, "meta").stream()
                .anyMatch(m -> "dcterms:modified".equals(m.attr("property")));
        if (!tieneModified) {
            Element meta = opf.createElement(prefijo + "meta");
            meta.attr("property", "dcterms:modified");
            meta.text(FECHA_UTC.format(Instant.now()));
            metadata.appendChild(meta);
        }

        String idioma = textoTag(opf, "language");
        if (idioma.isBlank()) {
            idioma = "es";
        }

        // nav: si no hay un item con properties="nav", generarlo (conservando el ncx).
        boolean tieneNav = todos(manifest, "item").stream()
                .anyMatch(it -> tieneToken(it.attr("properties"), "nav"));
        if (!tieneNav) {
            List<String[]> navEntries = entradasNav(entradas, manifest, spine, opfDir);
            Metadatos m = new Metadatos();
            m.idioma = idioma;
            String navHtml = construirNav(m, navEntries);
            entradas.put(opfDir + "nav.xhtml", navHtml.getBytes(StandardCharsets.UTF_8));

            Element item = opf.createElement(prefijo + "item");
            item.attr("id", idManifestLibre(manifest, "nav"));
            item.attr("href", "nav.xhtml");
            item.attr("media-type", "application/xhtml+xml");
            item.attr("properties", "nav");
            manifest.appendChild(item);
        }

        // 4. Serializar el OPF de vuelta.
        opf.outputSettings().syntax(Document.OutputSettings.Syntax.xml).prettyPrint(false).charset("UTF-8");
        entradas.put(opfPath, opf.outerHtml().getBytes(StandardCharsets.UTF_8));

        // 5. Reempaquetar (mimetype STORED primero).
        Files.createDirectories(salida.getParent());
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(salida.toFile()))) {
            escribirStored(zos, "mimetype", "application/epub+zip".getBytes(StandardCharsets.US_ASCII));
            for (Map.Entry<String, byte[]> en : entradas.entrySet()) {
                if (en.getKey().equals("mimetype")) {
                    continue;
                }
                escribirDeflated(zos, en.getKey(), en.getValue());
            }
        }
        log.info("EPUB 2 -> 3 convertido: {}", salida);
    }

    private static String rutaRootfile(String containerXml) {
        try {
            Document doc = Jsoup.parse(containerXml, "", Parser.xmlParser());
            Element rf = doc.selectFirst("rootfile");
            if (rf != null && !rf.attr("full-path").isBlank()) {
                return rf.attr("full-path");
            }
        } catch (Exception e) {
            log.warn("No se pudo leer container.xml: {}", e.getMessage());
        }
        return null;
    }

    private static void asegurarIdentificador(Document opf, Element pkg, Element metadata) {
        String uid = pkg.attr("unique-identifier");
        boolean existe = !uid.isBlank()
                && todos(opf, "identifier").stream().anyMatch(id -> uid.equals(id.attr("id")));
        if (!existe) {
            Element id = opf.createElement("dc:identifier");
            id.attr("id", "bookid");
            id.text("urn:uuid:" + UUID.randomUUID());
            metadata.appendChild(id);
            pkg.attr("unique-identifier", "bookid");
        }
    }

    private static String textoTag(Document opf, String local) {
        Element el = primero(opf, local);
        return (el != null) ? el.text().trim() : "";
    }

    /** Nombre local de un tag, sin prefijo de namespace ("opf:item" -> "item"). */
    private static String localName(Element el) {
        String t = el.tagName();
        int i = t.indexOf(':');
        return (i >= 0) ? t.substring(i + 1) : t;
    }

    /** Prefijo de namespace de un tag ("opf:package" -> "opf:"; "package" -> ""). */
    private static String prefijoDe(Element el) {
        String t = el.tagName();
        int i = t.indexOf(':');
        return (i >= 0) ? t.substring(0, i + 1) : "";
    }

    /** Primer descendiente (o el propio) cuyo nombre local coincide, ignorando prefijo. */
    private static Element primero(Element raiz, String local) {
        for (Element el : raiz.getAllElements()) {
            if (localName(el).equalsIgnoreCase(local)) {
                return el;
            }
        }
        return null;
    }

    /** Todos los elementos cuyo nombre local coincide, ignorando prefijo. */
    private static List<Element> todos(Element raiz, String local) {
        List<Element> res = new ArrayList<>();
        for (Element el : raiz.getAllElements()) {
            if (localName(el).equalsIgnoreCase(local)) {
                res.add(el);
            }
        }
        return res;
    }

    private static boolean tieneToken(String valor, String token) {
        if (valor == null || valor.isBlank()) {
            return false;
        }
        for (String t : valor.trim().split("\\s+")) {
            if (t.equals(token)) {
                return true;
            }
        }
        return false;
    }

    private static String idManifestLibre(Element manifest, String base) {
        java.util.Set<String> usados = new java.util.HashSet<>();
        for (Element it : todos(manifest, "item")) {
            usados.add(it.attr("id"));
        }
        if (!usados.contains(base)) {
            return base;
        }
        int n = 1;
        while (usados.contains(base + n)) {
            n++;
        }
        return base + n;
    }

    /** Entradas de navegación para el nav: primero desde toc.ncx; si no hay, desde el spine. */
    private static List<String[]> entradasNav(Map<String, byte[]> entradas, Element manifest,
            Element spine, String opfDir) {
        List<String[]> res = new ArrayList<>();

        String ncxHref = null;
        for (Element it : todos(manifest, "item")) {
            if ("application/x-dtbncx+xml".equals(it.attr("media-type"))) {
                ncxHref = it.attr("href");
                break;
            }
        }
        if (ncxHref != null) {
            byte[] ncxBytes = entradas.get(opfDir + ncxHref);
            if (ncxBytes != null) {
                try {
                    Document ncx = Jsoup.parse(new String(ncxBytes, StandardCharsets.UTF_8), "", Parser.xmlParser());
                    for (Element np : todos(ncx, "navPoint")) {
                        Element content = primero(np, "content");
                        Element label = primero(np, "navLabel");
                        if (content != null && !content.attr("src").isBlank()) {
                            String src = content.attr("src");
                            String txt = (label != null && !label.text().isBlank()) ? label.text().trim() : src;
                            res.add(new String[] { src, txt });
                        }
                    }
                } catch (Exception e) {
                    log.warn("No se pudo leer toc.ncx: {}", e.getMessage());
                }
            }
        }

        if (res.isEmpty()) {
            Map<String, String> hrefPorId = new LinkedHashMap<>();
            for (Element it : todos(manifest, "item")) {
                hrefPorId.put(it.attr("id"), it.attr("href"));
            }
            for (Element ref : todos(spine, "itemref")) {
                String href = hrefPorId.get(ref.attr("idref"));
                if (href == null) {
                    continue;
                }
                String label = href;
                byte[] b = entradas.get(opfDir + href);
                if (b != null) {
                    try {
                        String t = Jsoup.parse(new String(b, StandardCharsets.UTF_8)).title();
                        if (t != null && !t.isBlank()) {
                            label = t.trim();
                        }
                    } catch (Exception ignore) {
                        // usamos el href como etiqueta
                    }
                }
                res.add(new String[] { href, label });
            }
        }
        return res;
    }
}
