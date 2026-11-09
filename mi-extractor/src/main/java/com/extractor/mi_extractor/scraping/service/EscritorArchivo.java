package com.extractor.mi_extractor.scraping.service; // O el paquete donde esté
import com.extractor.mi_extractor.scraping.model.Capitulo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EscritorArchivo {

    private static final Logger log = LoggerFactory.getLogger(EscritorArchivo.class);

    public void guardarCapitulo(Capitulo capitulo, String rutaDeGuardadoBase, double numeroCapitulo) throws IOException {
        String titulo = capitulo.getTitulo();
        String contenidoHtml = capitulo.getContenidoHtml();

        // Guardar dentro de OEBPS/Text (ruta centralizada en rutaDestino para que "reanudar" pueda
        // comprobar la existencia con el mismo criterio).
        Path rutaCompleta = rutaDestino(rutaDeGuardadoBase, numeroCapitulo);
        // Crear carpetas necesarias si no existen (aunque DescargaWebService ya debería haberlas creado)
        Files.createDirectories(rutaCompleta.getParent());

        // Usamos la plantilla XHTML correcta (EPUB 2)
        String plantillaXHTML = construirPlantillaXHTML(titulo, contenidoHtml);
        Files.writeString(rutaCompleta, plantillaXHTML);
        log.debug("Guardado: {}", rutaCompleta);
    }

    /** Nombre de archivo de un capítulo: {@code CapituloNNNN.xhtml} o {@code CapituloNNNN_D.xhtml} (partes). */
    public static String nombreArchivo(double numeroCapitulo) {
        if (numeroCapitulo % 1 == 0) {
            return String.format("Capitulo%04d.xhtml", (int) numeroCapitulo);
        }
        String[] partes = String.valueOf(numeroCapitulo).split("\\.");
        int entero = Integer.parseInt(partes[0]);
        String decimal = partes[1];
        return String.format("Capitulo%04d_%s.xhtml", entero, decimal);
    }

    /** Ruta final de un capítulo dentro de la carpeta de la novela ({@code OEBPS/Text/...}). */
    public static Path rutaDestino(String rutaDeGuardadoBase, double numeroCapitulo) {
        return Paths.get(rutaDeGuardadoBase, "OEBPS", "Text", nombreArchivo(numeroCapitulo));
    }

    /** {@code true} si el capítulo ya está descargado en disco (para reanudar sin re-bajarlo). */
    public static boolean yaDescargado(String rutaDeGuardadoBase, double numeroCapitulo) {
        return Files.exists(rutaDestino(rutaDeGuardadoBase, numeroCapitulo));
    }

    // --- PLANTILLA XHTML PARA EPUB 3 (HTML5) ---
    private String construirPlantillaXHTML(String titulo, String contenidoHtml) {
        // El título viene del scraping: escaparlo para no romper el XHTML (&, <, >, ", ').
        String tituloSeguro = escaparXml(titulo);
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
             + "<!DOCTYPE html>\n"
             + "<html xmlns=\"http://www.w3.org/1999/xhtml\" xmlns:epub=\"http://www.idpf.org/2007/ops\" xml:lang=\"es\" lang=\"es\">\n"
             + "<head>\n"
             + "  <meta charset=\"utf-8\" />\n"
             + "  <title>" + tituloSeguro + "</title>\n"
             + "  <link rel=\"stylesheet\" type=\"text/css\" href=\"../Styles/stylesheet.css\" />\n"
             + "</head>\n"
             + "<body>\n"
             + "  <h1>" + tituloSeguro + "</h1>\n"
             + contenidoHtml
             + "\n</body>\n"
             + "</html>";
    }

    /** Escapa las entidades XML de un texto para interpolarlo con seguridad en el XHTML. */
    static String escaparXml(String texto) {
        if (texto == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(texto.length());
        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);
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
}