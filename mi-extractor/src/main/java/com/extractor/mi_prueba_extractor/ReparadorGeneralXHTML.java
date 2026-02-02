package com.extractor.mi_prueba_extractor;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Scanner;

public class ReparadorGeneralXHTML {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        System.out.println("--- Reparador General de Archivos XHTML para EPUB ---");
        System.out.println("Esta herramienta buscará y corregirá múltiples errores comunes de formato.");
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
        System.out.println("Se encontraron " + archivos.length + " capítulos. Empezando la revisión...");

        List<File> archivosConProblemas = new ArrayList<>();

        // --- FASE 1: DIAGNÓSTICO ---
        for (File archivo : archivos) {
            try {
                String contenido = Files.readString(archivo.toPath(), StandardCharsets.UTF_8);
                // Comprueba si el archivo tiene CUALQUIERA de los problemas conocidos
                if (contenido.contains("&nbsp;") ||
                    !contenido.startsWith("<?xml") ||
                    contenido.matches(".*(?i)<link[^>]+(?<!/)>.*") ||
                    contenido.matches(".*(?i)<hr[^>]*>(?!</hr>).*") ||
                    contenido.matches(".*(?i)<img[^>]+(?<!/)>.*") ||
                     contenido.matches(".*(?i)<markdown[^>]+(?<!/)>.*") ||
                    contenido.matches(".*(?i)<br>(?!</br>).*")) {
                    archivosConProblemas.add(archivo);
                }
            } catch (IOException e) {
                System.err.println("Error al leer el archivo: " + archivo.getName() + " - " + e.getMessage());
            }
        }

        // --- FASE 2: REPORTE Y CONFIRMACIÓN ---
        if (archivosConProblemas.isEmpty()) {
            System.out.println("\n ¡Perfecto! No se encontraron archivos que necesitaran reparación.");
        } else {
            System.out.println("\n--- Reporte de Archivos con Posibles Errores de Formato ---");
            for (File archivo : archivosConProblemas) {
                System.out.println(" -> " + archivo.getName());
            }
            System.out.println("\nSe encontraron " + archivosConProblemas.size() + " archivos con problemas.");
            System.out.print("¿Quieres intentar repararlos todos ahora? (s/n): ");
            String respuesta = scanner.nextLine();

            // --- FASE 3: REPARACIÓN ---
            if (respuesta.equalsIgnoreCase("s")) {
                int archivosReparados = 0;
                System.out.println("\nEmpezando la reparación...");
                for (File archivo : archivosConProblemas) {
                    try {
                        Path rutaArchivo = archivo.toPath();
                        String contenido = Files.readString(rutaArchivo, StandardCharsets.UTF_8);
                        String contenidoOriginal = contenido;

                        // 1. Añadir la declaración XML si falta
                        if (!contenido.trim().startsWith("<?xml")) {
                            contenido = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" + contenido;
                        }

                        // 2. Aplicar todas las reparaciones en cadena
                        contenido = contenido.replace("&nbsp;", "&#160;");
                        contenido = contenido.replaceAll("(?i)(<link[^>]+)(?<!/)>", "$1 />");
                        contenido = contenido.replaceAll("(?i)(<hr[^>]*)(?<!/)>", "$1 />");
                        contenido = contenido.replaceAll("(?i)(<img[^>]+)(?<!/)>", "$1 />");
                        contenido = contenido.replaceAll("(?i)(<markdown[^>]+)(?<!/)>", "$1 />");
                        contenido = contenido.replaceAll("(?i)<br>", "<br />");
                        
                        // Si hubo cambios, sobrescribir el archivo
                        if (!contenido.equals(contenidoOriginal)) {
                            Files.writeString(rutaArchivo, contenido, StandardCharsets.UTF_8);
                            System.out.println(" -> Archivo reparado: " + archivo.getName());
                            archivosReparados++;
                        }
                    } catch (IOException e) {
                        System.err.println("Error al reparar el archivo: " + archivo.getName());
                    }
                }
                System.out.println("\n Proceso de reparación finalizado. Se modificaron " + archivosReparados + " archivos.");
            } else {
                System.out.println("\nNo se realizó ninguna modificación.");
            }
        }
        scanner.close();
    }
}