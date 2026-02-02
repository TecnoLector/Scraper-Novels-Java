package com.extractor.mi_prueba_extractor;

import nl.siegmann.epublib.domain.Book;
import nl.siegmann.epublib.domain.Resource;
import nl.siegmann.epublib.epub.EpubWriter;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Scanner;

public class CreadorEpub {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        System.out.println("--- Creador de EPUBs ---");

        System.out.println("Introduce la ruta de la carpeta donde están tus capítulos .xhtml:");
        String rutaCarpeta = scanner.nextLine();
        
        System.out.println("Introduce el título del libro:");
        String tituloLibro = scanner.nextLine();

        System.out.println("Introduce el autor del libro:");
        String autorLibro = scanner.nextLine();

        System.out.println("Introduce el nombre del archivo de salida (ej: MiLibro.epub):");
        String archivoSalida = scanner.nextLine();

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

        // Ordenar los archivos para asegurar el orden correcto de los capítulos
        Arrays.sort(archivos, Comparator.comparing(File::getName));
        System.out.println("Se encontraron " + archivos.length + " capítulos. Empezando la creación del EPUB...");

        try {
            // 1. Crear un nuevo objeto Libro
            Book libro = new Book();

            // 2. Establecer los metadatos (título y autor)
            libro.getMetadata().addTitle(tituloLibro);
            libro.getMetadata().addAuthor(new nl.siegmann.epublib.domain.Author(autorLibro));

            // 3. Añadir cada capítulo al libro
            for (File archivo : archivos) {
                // Leemos el contenido del archivo
                FileInputStream fis = new FileInputStream(archivo);
                // Creamos un "Recurso" para EpubLib
                Resource recurso = new Resource(fis, archivo.getName());
                // Añadimos el recurso (el capítulo) al libro
                libro.addSection(archivo.getName().replace(".xhtml", ""), recurso);
            }

            // 4. Crear el archivo EPUB
            EpubWriter epubWriter = new EpubWriter();
            File epubFile = new File(carpeta.getPath(), archivoSalida);
            epubWriter.write(libro, new FileOutputStream(epubFile));

            System.out.println("\n✅ ¡Éxito! Se ha creado el archivo EPUB en:");
            System.out.println(epubFile.getAbsolutePath());

        } catch (IOException e) {
            System.err.println("Ocurrió un error al crear el EPUB: " + e.getMessage());
            e.printStackTrace();
        } finally {
            scanner.close();
        }
    }
}