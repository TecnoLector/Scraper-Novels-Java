package com.extractor.mi_prueba_extractor;

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RenombradorArchivos {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        System.out.println("--- Renombrador de Archivos de Capítulos ---");
        System.out.println("Introduce la ruta de la carpeta donde están tus capítulos .xhtml:");
        String rutaCarpeta = scanner.nextLine();

        File carpeta = new File(rutaCarpeta);

        if (!carpeta.exists() || !carpeta.isDirectory()) {
            System.err.println("Error: La ruta proporcionada no es una carpeta válida.");
            scanner.close();
            return;
        }

        // Obtiene solo los archivos .xhtml
        File[] archivos = carpeta.listFiles((dir, name) -> name.toLowerCase().endsWith(".xhtml"));

        if (archivos == null || archivos.length == 0) {
            System.err.println("No se encontraron archivos .xhtml en la carpeta.");
            scanner.close();
            return;
        }

        // Ordena los archivos para procesarlos correctamente
        Arrays.sort(archivos, Comparator.comparing(File::getName));
        System.out.println("Se encontraron " + archivos.length + " capítulos. Empezando a renombrar...");

        int archivosRenombrados = 0;
        // Expresión regular para encontrar el número en el nombre del archivo
        // Busca "Capitulo-", seguido de 4 dígitos
        Pattern pattern = Pattern.compile("Capitulo-(\\d{4})");

        for (File archivoActual : archivos) {
            String nombreActual = archivoActual.getName();
            Matcher matcher = pattern.matcher(nombreActual);

            // Si encuentra el patrón de número en el nombre del archivo
            if (matcher.find()) {
                String numeroCapitulo = matcher.group(1); // El grupo 1 son los 4 dígitos
                String nuevoNombre = "Capitulo" + numeroCapitulo + ".xhtml";
                
                // Crea la ruta completa del nuevo archivo
                File archivoNuevo = new File(carpeta.getPath(), nuevoNombre);

                // Intenta renombrar el archivo
                if (archivoActual.renameTo(archivoNuevo)) {
                    System.out.println("'" + nombreActual + "'  ->  '" + nuevoNombre + "'");
                    archivosRenombrados++;
                } else {
                    System.err.println("Error: No se pudo renombrar el archivo: " + nombreActual);
                }
            } else {
                System.out.println("Omitiendo archivo (no coincide con el formato esperado): " + nombreActual);
            }
        }

        System.out.println("\n✅ Proceso finalizado. Se renombraron " + archivosRenombrados + " archivos.");
        scanner.close();
    }
}