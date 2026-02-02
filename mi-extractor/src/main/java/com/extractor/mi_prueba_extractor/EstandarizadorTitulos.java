package com.extractor.mi_prueba_extractor;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EstandarizadorTitulos {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        System.out.println("--- Estandarizador de Títulos de Capítulos ---");
        System.out.println("Esta herramienta buscará y corregirá los títulos al formato 'Capítulo #: Nombre'.");
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
        System.out.println("Se encontraron " + archivos.length + " capítulos. Empezando análisis...");

        // Patrón para capturar el número y el resto del texto.
        Pattern patronExtraccion = Pattern.compile("(Cap[ií]tulo\\s*\\d+)(.*)", Pattern.CASE_INSENSITIVE);
        
        Map<File, String[]> cambiosPropuestos = new LinkedHashMap<>();

        // --- FASE 1: DIAGNÓSTICO ---
        for (File archivo : archivos) {
            try {
                String contenido = Files.readString(archivo.toPath(), StandardCharsets.UTF_8);
                Document doc = Jsoup.parse(contenido);
                Element h1 = doc.selectFirst("h1");

                if (h1 != null && h1.hasText()) {
                    String tituloOriginal = h1.text().trim();
                    Matcher matcher = patronExtraccion.matcher(tituloOriginal);

                    if (matcher.find()) {
                        String parteCapitulo = matcher.group(1).replaceAll("[ií]", "í");
                        String parteNombre = matcher.group(2);

                        // 1. Elimina cualquier carácter basura inicial (separadores).
                        String nombreLimpio = parteNombre.replaceAll("^[^a-zA-Z0-9¿¡'\"(]+", "").trim();
                        
                        // 2. --- NUEVO: REEMPLAZO CONTEXTUAL ---
                        // Reemplaza '?' por '’' solo si está entre dos letras.
                        nombreLimpio = nombreLimpio.replaceAll("([a-zA-Z])\\?([a-zA-Z])", "$1’$2");

                        if (nombreLimpio.isEmpty()) {
                            nombreLimpio = "(Título no encontrado)";
                        }

                        String numero = parteCapitulo.replaceAll("[^\\d]", "");
                        String tituloNuevo = "Capítulo " + numero + ": " + nombreLimpio;

                        if (!tituloOriginal.equals(tituloNuevo)) {
                            cambiosPropuestos.put(archivo, new String[]{tituloOriginal, tituloNuevo});
                        }
                    }
                }
            } catch (IOException e) {
                System.err.println("Error al leer el archivo: " + archivo.getName());
            }
        }

        // --- FASE 2 y 3 (Reporte, Confirmación y Reparación) no cambian ---
        if (cambiosPropuestos.isEmpty()) {
            System.out.println("\n✅ ¡Perfecto! Todos los títulos ya están en el formato estándar.");
        } else {
            System.out.println("\n--- Reporte de Cambios Propuestos ---");
            for (Map.Entry<File, String[]> entry : cambiosPropuestos.entrySet()) {
                System.out.println("Archivo: " + entry.getKey().getName());
                System.out.println("  -> Original: \"" + entry.getValue()[0] + "\"");
                System.out.println("  -> Nuevo:    \"" + entry.getValue()[1] + "\"");
            }
            
            System.out.println("\nSe proponen cambios en " + cambiosPropuestos.size() + " archivos.");
            System.out.print("¿Quieres aplicar todas estas correcciones ahora? (s/n): ");
            String respuesta = scanner.nextLine();

            if (respuesta.equalsIgnoreCase("s")) {
                int archivosModificados = 0;
                System.out.println("\nEmpezando la estandarización...");
                for (Map.Entry<File, String[]> entry : cambiosPropuestos.entrySet()) {
                    File archivo = entry.getKey();
                    String tituloNuevo = entry.getValue()[1];
                    try {
                        Path rutaArchivo = archivo.toPath();
                        String contenido = Files.readString(rutaArchivo, StandardCharsets.UTF_8);
                        Document doc = Jsoup.parse(contenido);

                        doc.selectFirst("h1").text(tituloNuevo);
                        doc.selectFirst("title").text(tituloNuevo);
                        
                        Files.writeString(rutaArchivo, doc.outerHtml(), StandardCharsets.UTF_8);
                        archivosModificados++;
                    } catch (IOException e) {
                        System.err.println("Error al modificar el archivo: " + archivo.getName());
                    }
                }
                System.out.println("\n✅ Proceso finalizado. Se modificaron " + archivosModificados + " archivos.");
            } else {
                System.out.println("\nNo se realizó ninguna modificación.");
            }
        }
        scanner.close();
    }
}