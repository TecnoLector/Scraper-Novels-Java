package com.extractor.mi_extractor;

import java.util.Scanner;

public class App {

    public static void main(String[] args) {

        Scanner scanner = new Scanner(System.in);
        System.out.println("--- Suite de Herramientas para Novelas v7.0 ---");

        while (true) {
            System.out.println("\n¿Qué suite de herramientas quieres usar?");
            System.out.println("1. Extractor de Capítulos (Descargar desde la web a .xhtml)");
            System.out.println("2. Editor de Capítulos (Limpiar .xhtml y Crear EPUB)");
            System.out.println("3. Utilidades de EPUB (Dividir, Descomprimir, Re-empaquetar)");
            System.out.println("0. Salir");
            System.out.print("Elige una opción: ");

            int opcion;
            try {
                opcion = scanner.nextInt();
            } catch (java.util.InputMismatchException e) {
                opcion = -1;
            }
            scanner.nextLine(); // Limpiar el buffer

            if (opcion == 1) {
                MenuExtractor.iniciar(scanner); 
            
            } else if (opcion == 2) {
                EditorXHTML.iniciar(scanner);
            
            } else if (opcion == 3) {
                UtilidadesEPUB.iniciar(scanner);

            } else if (opcion == 0) {
                break;
            } else {
                System.out.println("Opción no válida. Inténtalo de nuevo.");
            }
        }

        System.out.println("\nProceso finalizado. ¡Hasta luego!");
        scanner.close();
    }
}