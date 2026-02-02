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

public class ReparadorXHTML {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        System.out.println("--- Reparador de Archivos XHTML para EPUB ---");
        System.out.println("Esta herramienta revisará los encabezados de tus archivos.");
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

        String declaracionXml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>";
        List<File> archivosConProblemas = new ArrayList<>();

        // --- FASE 1: DIAGNÓSTICO ---
        for (File archivo : archivos) {
            try {
                List<String> lineas = Files.readAllLines(archivo.toPath(), StandardCharsets.UTF_8);

                // Si la primera línea no es la declaración XML correcta, lo añadimos a la lista
                if (lineas.isEmpty() || !lineas.get(0).trim().equals(declaracionXml)) {
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
            System.out.println("\n--- Reporte de Archivos con Encabezado Incorrecto ---");
            for (File archivo : archivosConProblemas) {
                System.out.println(" -> " + archivo.getName());
            }
            System.out.println("\nSe encontraron " + archivosConProblemas.size() + " archivos con problemas.");
            System.out.print("¿Quieres repararlos todos ahora? (s/n): ");
            String respuesta = scanner.nextLine();

            // --- FASE 3: REPARACIÓN (Solo si el usuario confirma) ---
            if (respuesta.equalsIgnoreCase("s")) {
                int archivosReparados = 0;
                System.out.println("\nEmpezando la reparación...");
                for (File archivo : archivosConProblemas) {
                    try {
                        Path rutaArchivo = archivo.toPath();
                        List<String> lineas = Files.readAllLines(rutaArchivo, StandardCharsets.UTF_8);
                        
                        StringBuilder nuevoContenido = new StringBuilder();
                        nuevoContenido.append(declaracionXml).append("\n");
                        
                        for (String linea : lineas) {
                            if (!linea.trim().toLowerCase().startsWith("<!doctype")) {
                                nuevoContenido.append(linea).append("\n");
                            }
                        }
                        
                        Files.writeString(rutaArchivo, nuevoContenido.toString(), StandardCharsets.UTF_8);
                        archivosReparados++;
                    } catch (IOException e) {
                        System.err.println("Error al reparar el archivo: " + archivo.getName());
                    }
                }
                System.out.println("\n Proceso de reparación finalizado. Se repararon " + archivosReparados + " archivos.");
            } else {
                System.out.println("\nNo se realizó ninguna modificación.");
            }
        }
        scanner.close();
    }
}