package com.extractor.mi_extractor; // O el paquete donde esté

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class EscritorArchivo {

    public void guardarCapitulo(Capitulo capitulo, String rutaDeGuardadoBase, int numeroCapitulo) throws IOException {
        String titulo = capitulo.getTitulo();
        String contenidoHtml = capitulo.getContenidoHtml();

        String nombreArchivo = String.format("Capitulo%04d.xhtml", numeroCapitulo);
        
        // --- CAMBIO: Guardar dentro de OEBPS/Text ---
        Path rutaCompleta = Paths.get(rutaDeGuardadoBase, "OEBPS", "Text", nombreArchivo);
        // Crear carpetas necesarias si no existen (aunque MenuExtractor ya debería haberlas creado)
        Files.createDirectories(rutaCompleta.getParent()); 
        // --- FIN CAMBIO ---

        // Usamos la plantilla XHTML correcta (EPUB 2)
        String plantillaXHTML = construirPlantillaXHTML(titulo, contenidoHtml);
        Files.writeString(rutaCompleta, plantillaXHTML);
        System.out.println(" ¡Éxito! Guardado como: " + rutaCompleta.toString());
    }

    // --- PLANTILLA XHTML CORRECTA (EPUB 2 / XHTML 1.1) ---
    private String construirPlantillaXHTML(String titulo, String contenidoHtml) {
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
             + "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.1//EN\"\n"
             + "  \"http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd\">\n"
             + "<html xmlns=\"http://www.w3.org/1999/xhtml\" xml:lang=\"es\">\n"
             + "<head>\n"
             + "  <title>" + titulo + "</title>\n"
             + "  <link rel=\"stylesheet\" type=\"text/css\" href=\"../Styles/stylesheet.css\" />\n"
             + "</head>\n"
             + "<body>\n"
             + "  <h1>" + titulo + "</h1>\n"
             + contenidoHtml
             + "\n</body>\n"
             + "</html>";
    }
}