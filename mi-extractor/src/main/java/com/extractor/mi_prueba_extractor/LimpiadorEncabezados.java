package com.extractor.mi_prueba_extractor;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.regex.Pattern;

public class LimpiadorEncabezados {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        System.out.println("--- Limpiador Interactivo de Encabezados (h1, h2, etc.) ---");
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

        Pattern patronTituloValido = Pattern.compile("^Cap[ií]tulo\\s+\\d+.*\\w.*", Pattern.CASE_INSENSITIVE);
        String[] etiquetasAProcesar = {"h1", "h2", "h3", "h4"}; 
        
        Map<File, List<String>> problemasEncontrados = new LinkedHashMap<>();

        // --- FASE 1: DIAGNÓSTICO ---
        for (File archivo : archivos) {
            try {
                String contenido = Files.readString(archivo.toPath(), StandardCharsets.UTF_8);
                Document doc = Jsoup.parse(contenido);
                
                for (String etiqueta : etiquetasAProcesar) {
                    Elements encabezados = doc.select(etiqueta);
                    
                    Element tituloPrincipal = null;
                    
                    for (Element encabezado : encabezados) {
                        if (encabezado.hasText() && patronTituloValido.matcher(encabezado.text().trim()).find()) {
                            if (tituloPrincipal == null) {
                                tituloPrincipal = encabezado;
                            } else {
                                // Ya hay un principal, este es basura
                                registrarProblema(problemasEncontrados, archivo, encabezado);
                            }
                        } else {
                            // No cumple el patrón, es basura
                            registrarProblema(problemasEncontrados, archivo, encabezado);
                        }
                    }
                }
            } catch (IOException e) {
                System.err.println("Error al leer el archivo: " + archivo.getName() + " - " + e.getMessage());
            }
        }

        // --- FASE 2: REPORTE Y CONFIRMACIÓN ---
        if (problemasEncontrados.isEmpty()) {
            System.out.println("\n ¡Perfecto! No se encontraron encabezados basura que eliminar.");
        } else {
            System.out.println("\n--- Reporte de Encabezados Basura Encontrados ---");
            for (Map.Entry<File, List<String>> entry : problemasEncontrados.entrySet()) {
                System.out.println("En el archivo '" + entry.getKey().getName() + "' se propone eliminar:");
                for (String textoBasura : entry.getValue()) {
                    System.out.println("  -> " + textoBasura);
                }
            }
            
            System.out.println("\nSe encontraron problemas en " + problemasEncontrados.size() + " archivos.");
            System.out.print("¿Quieres proceder con la eliminación? (s/n): ");
            String respuesta = scanner.nextLine();

            // --- FASE 3: REPARACIÓN ---
            if (respuesta.equalsIgnoreCase("s")) {
                int archivosModificados = 0;
                System.out.println("\nEmpezando la limpieza...");
                for (File archivo : problemasEncontrados.keySet()) {
                     try {
                        Path rutaArchivo = archivo.toPath();
                        String contenido = Files.readString(rutaArchivo, StandardCharsets.UTF_8);
                        Document doc = Jsoup.parse(contenido);
                        boolean archivoCambiado = false;
                        for (String etiqueta : etiquetasAProcesar) {
                            Elements encabezados = doc.select(etiqueta);
                            Element tituloPrincipal = null;
                            List<Element> paraBorrar = new ArrayList<>();
                            for (Element encabezado : encabezados) {
                                if (encabezado.hasText() && patronTituloValido.matcher(encabezado.text().trim()).find()) {
                                    if (tituloPrincipal == null) tituloPrincipal = encabezado;
                                    else paraBorrar.add(encabezado);
                                } else {
                                    paraBorrar.add(encabezado);
                                }
                            }
                            if (!paraBorrar.isEmpty()) {
                                for (Element el : paraBorrar) el.remove();
                                archivoCambiado = true;
                            }
                        }
                        if (archivoCambiado) {
                            Files.writeString(rutaArchivo, doc.outerHtml(), StandardCharsets.UTF_8);
                            archivosModificados++;
                        }
                    } catch (IOException e) {
                        System.err.println("Error al reparar el archivo: " + archivo.getName());
                    }
                }
                System.out.println("\n Proceso finalizado. Se modificaron " + archivosModificados + " archivos.");
            } else {
                System.out.println("\nNo se realizó ninguna modificación.");
            }
        }
        scanner.close();
    }
    
    private static void registrarProblema(Map<File, List<String>> mapa, File archivo, Element elemento) {
        mapa.computeIfAbsent(archivo, k -> new ArrayList<>()).add(
            "<" + elemento.tagName() + ">: \"" + elemento.text() + "\""
        );
    }
}