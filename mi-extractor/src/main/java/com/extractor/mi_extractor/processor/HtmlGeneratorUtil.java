package com.extractor.mi_extractor.processor;

import io.documentnode.epub4j.domain.Metadata;

// Clase de utilidades pura, no necesita @Component
public class HtmlGeneratorUtil {

    // 1. El método que usa el InsertPageBookProcessor
    public static String generarPaginaTituloLibro(String nombreLibro, String sitioExtraccion, String creadorArchivo, int numLibro) {
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

    public static String generarPaginaMetadatosXHTML(Metadata metadataOriginal, int startChapNum, int endChapNum,
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
}