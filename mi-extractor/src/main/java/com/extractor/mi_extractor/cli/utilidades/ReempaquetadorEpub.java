package com.extractor.mi_extractor.cli.utilidades;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Scanner;
import java.util.stream.Stream;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Re-empaquetado de una carpeta como EPUB (mimetype STORED primero), extraído de
 * {@link UtilidadesEPUB} sin cambios de comportamiento. Conserva el mismo estado.
 */
class ReempaquetadorEpub {
private static final Logger log = LoggerFactory.getLogger(ReempaquetadorEpub.class);

    private final Scanner scanner;
    private final String rutaCarpeta;

    ReempaquetadorEpub(Scanner scanner, String rutaCarpeta) {
        this.scanner = scanner;
        this.rutaCarpeta = rutaCarpeta;
    }

    void reempaquetarComoEpub() {
        System.out.println("Introduce el nombre de la CARPETA que quieres empaquetar (ej: MiLibro_extraido):");
        String nombreCarpetaFuente = scanner.nextLine();
        Path rutaCarpetaFuente = Paths.get(rutaCarpeta, nombreCarpetaFuente);

        // --- Validaciones Esenciales ---
        if (!Files.isDirectory(rutaCarpetaFuente)) {
            System.err.println("Error: La carpeta '" + nombreCarpetaFuente + "' no existe.");
            return;
        }
        Path rutaMimetype = rutaCarpetaFuente.resolve("mimetype");
        if (!Files.exists(rutaMimetype)) {
            System.err.println("Error: La carpeta no contiene el archivo 'mimetype' en su raíz.");
            return;
        }
        if (!Files.isDirectory(rutaCarpetaFuente.resolve("META-INF"))) {
            System.err.println("Error: La carpeta no contiene el directorio 'META-INF'.");
            return;
        }
        // ---------------------------------

        System.out.println("Introduce el nombre del nuevo archivo EPUB de salida (ej: MiLibro_final.epub):");
        String nombreEpubSalida = scanner.nextLine();
        Path rutaEpubSalida = Paths.get(rutaCarpeta, nombreEpubSalida);

        try (
                // 1. Creamos el archivo .epub de salida
                FileOutputStream fos = new FileOutputStream(rutaEpubSalida.toFile());
                ZipOutputStream zos = new ZipOutputStream(fos)) {
            System.out.println("Empaquetando...");

            // --- REGLA 1 y 2: Añadir 'mimetype' PRIMERO y SIN COMPRESIÓN ---

            // Leemos los bytes del archivo mimetype
            byte[] mimetypeBytes = Files.readAllBytes(rutaMimetype);

            // Creamos una entrada Zip
            ZipEntry mimetypeEntry = new ZipEntry("mimetype");

            // REGLA 2: Sin compresión (STORED)
            mimetypeEntry.setMethod(ZipEntry.STORED);

            // Para el método STORED, necesitamos saber el tamaño y el CRC-32 ANTES de
            // escribirlo
            mimetypeEntry.setSize(mimetypeBytes.length);
            mimetypeEntry.setCompressedSize(mimetypeBytes.length);
            CRC32 crc = new CRC32();
            crc.update(mimetypeBytes);
            mimetypeEntry.setCrc(crc.getValue());

            // REGLA 1: Escribimos la entrada 'mimetype' PRIMERO
            zos.putNextEntry(mimetypeEntry);
            zos.write(mimetypeBytes);
            zos.closeEntry();

            // --- FIN DE REGLAS ESPECIALES ---

            // 3. Añadimos el RESTO de archivos (comprimidos)

            // Usamos Files.walk para recorrer recursivamente todas las carpetas y archivos
            try (Stream<Path> paths = Files.walk(rutaCarpetaFuente)) {

                paths.filter(Files::isRegularFile)
                        .filter(path -> !path.getFileName().toString().equals("mimetype"))
                        .forEach(path -> {
                            try {
                                // Creamos el nombre de la ruta dentro del ZIP
                                // ej: "OEBPS/Text/Capitulo0001.xhtml"
                                String zipPath = rutaCarpetaFuente.relativize(path).toString().replace(File.separator,
                                        "/");

                                ZipEntry entry = new ZipEntry(zipPath);
                                entry.setMethod(ZipEntry.DEFLATED);

                                zos.putNextEntry(entry);
                                Files.copy(path, zos);
                                zos.closeEntry();

                            } catch (IOException e) {
                                System.err.println("Error añadiendo el archivo: " + path);
                            }
                        });
            }

            System.out.println("¡EPUB re-empaquetado con éxito en '" + nombreEpubSalida + "'!");

        } catch (IOException e) {
            System.err.println("Error al crear el archivo EPUB: " + e.getMessage());
            log.error("Excepción no controlada", e);
        }
    }
}
