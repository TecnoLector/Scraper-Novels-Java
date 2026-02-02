package com.extractor.mi_prueba_extractor;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Scanner;
import java.util.regex.Pattern;

public class ValidadorTitulos {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        System.out.println("--- Herramienta de Validación de Capítulos ---");
        System.out.println("Introduce la ruta de la carpeta donde están tus capítulos .xhtml:");
        String rutaCarpeta = scanner.nextLine();

        // --- NUEVO MENÚ DE OPCIONES ---
        System.out.println("\n¿Qué validación quieres realizar?");
        System.out.println("1. Revisar que el título <h1> siga el formato 'Capítulo # Nombre'.");
        System.out.println("2. Comparar que el título <h1> y la etiqueta <title> sean idénticos.");
        System.out.print("Elige una opción (1 o 2): ");
        int opcion = scanner.nextInt();
        // --------------------------------

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
        System.out.println("\nSe encontraron " + archivos.length + " capítulos. Empezando la validación...");
        
        // Ejecutar la validación elegida
        if (opcion == 1) {
            validarFormatoTituloH1(archivos);
        } else if (opcion == 2) {
            validarH1vsTitle(archivos);
        } else {
            System.out.println("Opción no válida.");
        }

        scanner.close();
    }

    /**
     * Opción 1: Revisa que el <h1> siga el formato "Capítulo # Nombre".
     */
    private static void validarFormatoTituloH1(File[] archivos) {
        Pattern patronTituloValido = Pattern.compile("^Cap[ií]tulo\\s+\\d+.*\\w.*", Pattern.CASE_INSENSITIVE);
        int titulosInvalidos = 0;
        System.out.println("\n--- Reporte de Títulos que NO cumplen el formato 'Capítulo # Nombre' ---");

        for (File archivo : archivos) {
            try {
                String contenido = Files.readString(archivo.toPath(), StandardCharsets.UTF_8);
                Document doc = Jsoup.parse(contenido);
                Element tituloElement = doc.selectFirst("h1");

                if (tituloElement != null && tituloElement.hasText()) {
                    String textoTitulo = tituloElement.text().trim();
                    if (!patronTituloValido.matcher(textoTitulo).find()) {
                        System.out.println("Archivo: " + archivo.getName());
                        System.out.println("  -> Título Inválido o Incompleto: \"" + textoTitulo + "\"");
                        titulosInvalidos++;
                    }
                } else {
                    System.out.println("Archivo: " + archivo.getName() + " -> Error: No se encontró la etiqueta <h1> del título.");
                    titulosInvalidos++;
                }
            } catch (IOException e) {
                System.err.println("Error al procesar el archivo: " + archivo.getName() + " - " + e.getMessage());
            }
        }

        if (titulosInvalidos == 0) {
            System.out.println("\n¡Perfecto! Todos los títulos tienen el formato correcto.");
        } else {
            System.out.println("\nSe encontraron " + titulosInvalidos + " archivos con títulos que no siguen el formato esperado.");
        }
    }

    /**
     * Opción 2: Compara si el texto de <h1> y <title> son iguales.
     */
    private static void validarH1vsTitle(File[] archivos) {
        int titulosNoCoinciden = 0;
        System.out.println("\n--- Reporte de Títulos que NO coinciden entre <h1> y <title> ---");

        for (File archivo : archivos) {
            try {
                String contenido = Files.readString(archivo.toPath(), StandardCharsets.UTF_8);
                Document doc = Jsoup.parse(contenido);

                Element h1Element = doc.selectFirst("h1");
                Element titleElement = doc.selectFirst("title");

                if (h1Element != null && titleElement != null) {
                    String textoH1 = h1Element.text().trim();
                    String textoTitle = titleElement.text().trim();

                    if (!textoH1.equals(textoTitle)) {
                        System.out.println("Archivo: " + archivo.getName());
                        System.out.println("  -> H1:    \"" + textoH1 + "\"");
                        System.out.println("  -> Title: \"" + textoTitle + "\"");
                        titulosNoCoinciden++;
                    }
                } else {
                    System.out.println("Archivo: " + archivo.getName() + " -> Error: No se encontró la etiqueta <h1> o <title>.");
                    titulosNoCoinciden++;
                }
            } catch (IOException e) {
                System.err.println("Error al procesar el archivo: " + archivo.getName() + " - " + e.getMessage());
            }
        }

        if (titulosNoCoinciden == 0) {
            System.out.println("\n¡Perfecto! Todos los títulos <h1> y <title> coinciden.");
        } else {
            System.out.println("\nSe encontraron " + titulosNoCoinciden + " archivos donde los títulos no coinciden.");
        }
    }
}