package com.extractor.mi_extractor;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Scanner;

/**
 * Conversión a EPUB 3 nativo (sin Calibre) sobre la carpeta de trabajo: un archivo
 * (opción 5) o todos los de la carpeta (opción 6). Extraído de {@link UtilidadesEPUB}
 * sin cambios de comportamiento. Conserva el mismo estado: el {@link Scanner} y la carpeta.
 */
class ConversorEpub3Util {

    private final Scanner scanner;
    private final String rutaCarpeta;

    ConversorEpub3Util(Scanner scanner, String rutaCarpeta) {
        this.scanner = scanner;
        this.rutaCarpeta = rutaCarpeta;
    }

    /** Opción 5: pide el nombre y convierte UN EPUB 2 a v3 (nativo), sin tocar el original. */
    void convertirUno() {
        System.out.println("Nombre del EPUB 2 a convertir (ej: Libro.epub):");
        String nombreArchivo = scanner.nextLine().trim();
        Path rutaInput = Paths.get(this.rutaCarpeta, nombreArchivo);
        String nombreSalida = nombreArchivo.toLowerCase().endsWith(".epub")
                ? nombreArchivo.substring(0, nombreArchivo.length() - 5) + "_v3.epub"
                : nombreArchivo + "_v3.epub";
        Path rutaOutput = Paths.get(this.rutaCarpeta, nombreSalida);

        if (!Files.exists(rutaInput)) {
            System.err.println("\n❌ Error: Java no ve el archivo: " + nombreArchivo);
            System.err.println("Ruta buscada: " + rutaInput.toAbsolutePath());

            System.out.println("\n--- Archivos disponibles en esta carpeta ---");
            File carpetaActual = new File(this.rutaCarpeta);
            File[] listaArchivos = carpetaActual.listFiles((dir, name) -> name.endsWith(".epub"));

            if (listaArchivos != null && listaArchivos.length > 0) {
                for (File f : listaArchivos) {
                    System.out.println(" -> " + f.getName());
                }
                System.out.println("--------------------------------------------");
                System.out.println("Consejo: Copia y pega el nombre EXACTO de la lista de arriba.");
            } else {
                System.err.println("(La carpeta parece vacía o no tiene archivos .epub)");
            }
            return;
        }

        // Conversión NATIVA (sin Calibre): genera un .epub v3 NUEVO, no toca el original.
        try {
            new EmpaquetadorEpub3().convertirEpub2aEpub3(rutaInput, rutaOutput);
            System.out.println("\n✅ EPUB 3 creado: " + rutaOutput.toAbsolutePath());
        } catch (Exception ex) {
            System.err.println("❌ Error al convertir a EPUB 3: " + ex.getMessage());
        }
    }

    /** Opción 6: convierte a EPUB 3 todos los .epub de la carpeta (saltando los *_v3.epub). */
    void convertirTodos() {
        File carpeta = new File(this.rutaCarpeta);
        File[] epubs = carpeta.listFiles((dir, name) -> {
            String n = name.toLowerCase();
            return n.endsWith(".epub") && !n.endsWith("_v3.epub");
        });
        if (epubs == null || epubs.length == 0) {
            System.out.println("No hay archivos .epub (sin _v3) en la carpeta.");
            return;
        }
        System.out.println("Convirtiendo " + epubs.length + " EPUB a v3...");
        EmpaquetadorEpub3 emp = new EmpaquetadorEpub3();
        int ok = 0;
        int fallos = 0;
        for (File f : epubs) {
            String base = f.getName().substring(0, f.getName().length() - 5); // quita ".epub"
            Path salida = Paths.get(this.rutaCarpeta, base + "_v3.epub");
            try {
                emp.convertirEpub2aEpub3(f.toPath(), salida);
                System.out.println("  ✓ " + f.getName() + " -> " + salida.getFileName());
                ok++;
            } catch (Exception e) {
                System.err.println("  ✗ " + f.getName() + ": " + e.getMessage());
                fallos++;
            }
        }
        System.out.println("Hecho. Convertidos: " + ok + ", fallos: " + fallos);
    }
}
