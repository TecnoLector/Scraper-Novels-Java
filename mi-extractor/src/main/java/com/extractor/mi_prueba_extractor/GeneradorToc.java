package com.extractor.mi_prueba_extractor;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Scanner;

public class GeneradorToc {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        System.out.println("--- Generador de Tabla de Contenido (toc.html) ---");
        System.out.println("Introduce la ruta de la carpeta donde están tus capítulos .xhtml:");
        String rutaCarpeta = scanner.nextLine();

        File carpeta = new File(rutaCarpeta);

        if (!carpeta.exists() || !carpeta.isDirectory()) {
            System.err.println("Error: La ruta proporcionada no es una carpeta válida.");
            scanner.close();
            return;
        }

        File[] archivos = carpeta.listFiles((dir, name) -> name.toLowerCase().endsWith(".xhtml"));

        if (archivos == null || archivos.length == 0) {
            System.err.println("No se encontraron archivos .xhtml en la carpeta.");
            scanner.close();
            return;
        }

        Arrays.sort(archivos, Comparator.comparing(File::getName));
        System.out.println("Se encontraron " + archivos.length + " capítulos. Procesando...");

        StringBuilder tocBuilder = new StringBuilder();
        tocBuilder.append("<!DOCTYPE html>\n");
        tocBuilder.append("<html>\n<head>\n  <title>Tabla de Contenido</title>\n</head>\n<body>\n");
        tocBuilder.append("  <h1>Índice de la Novela</h1>\n");
        tocBuilder.append("  <nav epub:type=\"toc\">\n    <h2>Índice</h2>\n    <ol>\n");

        for (File archivo : archivos) {
            try {
                String contenido = Files.readString(archivo.toPath(), StandardCharsets.UTF_8);
                Document doc = Jsoup.parse(contenido);
                
                // --- CAMBIO CLAVE: Buscamos el título en <p><strong> en lugar de <h1> ---
                Element tituloElement = doc.selectFirst("p > strong:contains(Capítulo, Capitulo)");  
                
                String tituloCapitulo = (tituloElement != null) ? tituloElement.text() : archivo.getName();
                String nombreArchivo = archivo.getName();

                tocBuilder.append("      <li><a href=\"").append(nombreArchivo).append("\">").append(tituloCapitulo).append("</a></li>\n");

            } catch (IOException e) {
                System.err.println("Error al leer el archivo: " + archivo.getName() + " - " + e.getMessage());
            }
        }

        tocBuilder.append("    </ol>\n  </nav>\n</body>\n</html>");

        Path rutaSalida = Paths.get(rutaCarpeta, "toc.html");
        try {
            Files.writeString(rutaSalida, tocBuilder.toString());
            System.out.println("\n✅ ¡Éxito! Se ha creado el archivo 'toc.html' en:");
            System.out.println(rutaSalida.toString());
        } catch (IOException e) {
            System.err.println("Error al guardar el archivo toc.html: " + e.getMessage());
        }

        scanner.close();
    }
}