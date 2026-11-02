package com.extractor.mi_extractor;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Scanner;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Descompresión de un EPUB a carpeta, extraída de {@link UtilidadesEPUB} sin cambios de
 * comportamiento. Conserva el mismo estado: el {@link Scanner} y la carpeta de trabajo.
 */
class DescompresorEpub {

    private final Scanner scanner;
    private final String rutaCarpeta;

    DescompresorEpub(Scanner scanner, String rutaCarpeta) {
        this.scanner = scanner;
        this.rutaCarpeta = rutaCarpeta;
    }

    void descomprimirEpub() {
        System.out.println("Introduce el nombre del archivo EPUB a descomprimir (ej: MiLibro.epub):");
        String nombreEpub = scanner.nextLine();
        Path rutaEpub = Paths.get(rutaCarpeta, nombreEpub);

        if (!Files.exists(rutaEpub)) {
            System.err.println("Error: El archivo " + nombreEpub + " no se encuentra.");
            return;
        }

        String nombreCarpetaDestino;
        if (nombreEpub.toLowerCase().endsWith(".epub")) {
            nombreCarpetaDestino = nombreEpub.substring(0, nombreEpub.length() - 5);
        } else {
            nombreCarpetaDestino = nombreEpub + "_extraido";
        }

        Path rutaDestino = Paths.get(rutaCarpeta, nombreCarpetaDestino);
        if (Files.exists(rutaDestino)) {
            System.err.println("Error: La carpeta de destino '" + nombreCarpetaDestino + "' ya existe.");
            System.err.println("Por favor, renómbrala o bórrala y vuelve a intentarlo.");
            return;
        }

        System.out.println("Descomprimiendo en: " + rutaDestino.toAbsolutePath());

        try {
            Files.createDirectories(rutaDestino);

            // 2. Abrir el archivo EPUB como un ZipInputStream
            try (ZipInputStream zis = new ZipInputStream(
                    new BufferedInputStream(new FileInputStream(rutaEpub.toFile())))) {

                ZipEntry zipEntry;
                byte[] buffer = new byte[1024];

                // 3. Recorrer cada "entrada" (archivo/carpeta) dentro del ZIP
                while ((zipEntry = zis.getNextEntry()) != null) {

                    Path nuevoArchivo = rutaDestino.resolve(zipEntry.getName());
                    if (!nuevoArchivo.toAbsolutePath().startsWith(rutaDestino.toAbsolutePath())) {
                        throw new IOException("Entrada de ZIP peligrosa: " + zipEntry.getName());
                    }

                    if (zipEntry.isDirectory()) {
                        // 5. Si es un directorio, lo creamos
                        Files.createDirectories(nuevoArchivo);
                    } else {
                        // 6. Si es un archivo:
                        Files.createDirectories(nuevoArchivo.getParent());

                        try (FileOutputStream fos = new FileOutputStream(nuevoArchivo.toFile())) {
                            int len;
                            while ((len = zis.read(buffer)) > 0) {
                                fos.write(buffer, 0, len);
                            }
                        }
                    }
                    zis.closeEntry();
                }
            }

            System.out.println("¡EPUB descomprimido con éxito en la carpeta '" + nombreCarpetaDestino + "'!");

        } catch (IOException e) {
            System.err.println("Error al descomprimir el EPUB: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
