package com.extractor.mi_extractor;

import java.io.File;
import java.util.Scanner;

/**
 * Menú CLI de las Utilidades de EPUB. Adaptador delgado: pide la carpeta de trabajo y
 * delega cada operación en su colaborador ({@link DivisorEpub}, {@link DescompresorEpub},
 * {@link ReempaquetadorEpub}, {@link InsertorPaginasLibro}, {@link ConversorEpub3Util}).
 */
public class UtilidadesEPUB {

    private Scanner scanner;
    private String rutaCarpeta;

    public UtilidadesEPUB(Scanner scanner) {
        this.scanner = scanner;
        System.out.println("Introduce la ruta de la carpeta donde están los archivos .epub a procesar:");
        this.rutaCarpeta = scanner.nextLine();
        File carpeta = new File(this.rutaCarpeta);
        if (!carpeta.exists() || !carpeta.isDirectory()) {
            System.err.println("Advertencia: La carpeta de trabajo especificada no existe o no es válida.");
        }
    }

    public static void iniciar(Scanner scanner) {
        UtilidadesEPUB util = new UtilidadesEPUB(scanner);
        util.mostrarMenu();
    }

    public void mostrarMenu() {
        while (true) {
            System.out.println("\n--- Utilidades de EPUB ---");
            System.out.println("Carpeta de trabajo: " + this.rutaCarpeta);
            System.out.println("1. Dividir EPUB...");
            System.out.println("2. Descomprimir un EPUB");
            System.out.println("3. Re-empaquetar carpeta como EPUB");
            System.out.println("4. Insertar paginas de 'Inicio de Libro'");
            System.out.println("5. Convertir un EPUB de v2 a v3 (nativo)");
            System.out.println("6. Convertir TODOS los EPUB de la carpeta a v3 (nativo)");
            System.out.println("0. Volver al menú principal");
            System.out.print("Elige una herramienta: ");

            int opcion;
            try {
                opcion = scanner.nextInt();
            } catch (java.util.InputMismatchException e) {
                opcion = -1;
            }
            scanner.nextLine();

            switch (opcion) {
                case 1:
                    new DivisorEpub(scanner, rutaCarpeta).mostrarMenuDividir();
                    break;
                case 2:
                    new DescompresorEpub(scanner, rutaCarpeta).descomprimirEpub();
                    break;
                case 3:
                    new ReempaquetadorEpub(scanner, rutaCarpeta).reempaquetarComoEpub();
                    break;
                case 4:
                    new InsertorPaginasLibro(scanner, rutaCarpeta).insertarPaginasDeLibro();
                    break;
                case 5:
                    new ConversorEpub3Util(scanner, rutaCarpeta).convertirUno();
                    break;
                case 6:
                    new ConversorEpub3Util(scanner, rutaCarpeta).convertirTodos();
                    break;
                default:
                    System.out.println("Opción no válida.");
            }
        }
    }
}
