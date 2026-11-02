package com.extractor.mi_extractor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Valida el empaquetado EPUB 3 nativo sin abrir el navegador ni usar epub4j. */
class EmpaquetadorEpub3Test {

    @TempDir
    Path tmp;

    private static String capituloXhtml(String titulo) {
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<!DOCTYPE html>\n"
             + "<html xmlns=\"http://www.w3.org/1999/xhtml\"><head><meta charset=\"utf-8\"/>"
             + "<title>" + titulo + "</title></head><body><h1>" + titulo + "</h1><p>texto</p></body></html>";
    }

    @Test
    void generaEpub3ConMimetypeStoredOpf3NavYNcx() throws Exception {
        Path oebps = tmp.resolve("OEBPS");
        Files.createDirectories(oebps.resolve("Text"));
        Files.createDirectories(oebps.resolve("Styles"));
        Files.createDirectories(oebps.resolve("Images"));
        Files.writeString(oebps.resolve("Text/Capitulo0001.xhtml"), capituloXhtml("Capítulo 1"));
        Files.writeString(oebps.resolve("Text/Capitulo0002.xhtml"), capituloXhtml("Capítulo 2"));
        Files.writeString(oebps.resolve("Styles/stylesheet.css"), "body{}");
        Files.write(oebps.resolve("Images/portada.jpg"), new byte[] { 1, 2, 3 });

        EmpaquetadorEpub3.Metadatos meta = new EmpaquetadorEpub3.Metadatos();
        meta.titulo = "Mi Novela";
        meta.autor = "Autor";
        meta.idioma = "es";
        meta.coverHref = "Images/portada.jpg";

        List<File> caps = List.of(
                oebps.resolve("Text/Capitulo0001.xhtml").toFile(),
                oebps.resolve("Text/Capitulo0002.xhtml").toFile());
        Path epub = tmp.resolve("salida.epub");

        new EmpaquetadorEpub3().crear(tmp, meta, caps, "<html><body>info</body></html>", epub);

        Map<String, String> contenidos = new HashMap<>();
        String primera = null;
        int primerMetodo = -1;
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(epub))) {
            ZipEntry e;
            boolean first = true;
            while ((e = zis.getNextEntry()) != null) {
                if (first) {
                    primera = e.getName();
                    primerMetodo = e.getMethod();
                    first = false;
                }
                contenidos.put(e.getName(), new String(zis.readAllBytes(), StandardCharsets.UTF_8));
            }
        }

        // mimetype: primera entrada y STORED (requisito EPUB)
        assertEquals("mimetype", primera);
        assertEquals(ZipEntry.STORED, primerMetodo);
        assertEquals("application/epub+zip", contenidos.get("mimetype"));

        // estructura básica
        assertTrue(contenidos.containsKey("META-INF/container.xml"));
        assertTrue(contenidos.containsKey("OEBPS/content.opf"));
        assertTrue(contenidos.containsKey("OEBPS/nav.xhtml"));
        assertTrue(contenidos.containsKey("OEBPS/toc.ncx"));
        assertTrue(contenidos.containsKey("OEBPS/Text/info_page.xhtml"));
        assertTrue(contenidos.containsKey("OEBPS/Styles/stylesheet.css"));
        assertTrue(contenidos.containsKey("OEBPS/Images/portada.jpg"));

        // OPF versión 3.0, capítulos en manifest/spine, portada marcada
        String opf = contenidos.get("OEBPS/content.opf");
        assertTrue(opf.contains("version=\"3.0\""), "el OPF debe ser 3.0");
        assertTrue(opf.contains("Text/Capitulo0001.xhtml") && opf.contains("Text/Capitulo0002.xhtml"));
        assertTrue(opf.contains("properties=\"cover-image\""), "portada marcada como cover-image");
        assertTrue(opf.contains("<dc:title>Mi Novela</dc:title>"));
        assertTrue(opf.contains("dcterms:modified"));

        // nav EPUB 3 con el título leído del capítulo
        String nav = contenidos.get("OEBPS/nav.xhtml");
        assertTrue(nav.contains("epub:type=\"toc\""));
        assertTrue(nav.contains("Capítulo 1") && nav.contains("Capítulo 2"));
    }

    @Test
    void portadaInexistenteSeOmiteSinRomper() throws Exception {
        Path oebps = tmp.resolve("OEBPS");
        Files.createDirectories(oebps.resolve("Text"));
        Files.writeString(oebps.resolve("Text/Capitulo0001.xhtml"), capituloXhtmlSimple());

        EmpaquetadorEpub3.Metadatos meta = new EmpaquetadorEpub3.Metadatos();
        meta.titulo = "X";
        meta.coverHref = "Images/no-existe.jpg";

        Path epub = tmp.resolve("x.epub");
        new EmpaquetadorEpub3().crear(tmp, meta,
                List.of(oebps.resolve("Text/Capitulo0001.xhtml").toFile()), null, epub);

        String opf;
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(epub))) {
            opf = leerEntrada(zis, "OEBPS/content.opf");
        }
        assertTrue(opf.contains("version=\"3.0\""));
        assertTrue(!opf.contains("cover-image"), "sin portada no debe quedar referencia a cover-image");
    }

    private static String capituloXhtmlSimple() {
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<!DOCTYPE html>\n<html xmlns=\"http://www.w3.org/1999/xhtml\">"
             + "<head><meta charset=\"utf-8\"/><title>C1</title></head><body><p>x</p></body></html>";
    }

    private static String leerEntrada(ZipInputStream zis, String nombre) throws Exception {
        ZipEntry e;
        while ((e = zis.getNextEntry()) != null) {
            if (e.getName().equals(nombre)) {
                return new String(zis.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
        return "";
    }
}
