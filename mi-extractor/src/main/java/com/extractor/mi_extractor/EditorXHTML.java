package com.extractor.mi_extractor;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Scanner;

/**
 * Menú CLI del Editor XHTML/EPUB. Adaptador delgado: pide la carpeta de trabajo y delega
 * las operaciones en sus colaboradores: {@link EditorXhtmlArchivos} (archivos .xhtml),
 * {@link CreadorEpub} (crear EPUB) y {@link EditorEpubLibro} (editar el .epub cargado).
 */
public class EditorXHTML {

    private Scanner scanner;
    private String rutaCarpeta;
    private File[] archivosXHTML;
    private EditorEpubLibro epubOps;

    public EditorXHTML(Scanner scanner) {
        this.scanner = scanner;
        System.out.println("Introduce la ruta de la carpeta de trabajo (donde están los .xhtml o el .epub):");
        this.rutaCarpeta = scanner.nextLine();

        actualizarListaXHTML();
        this.epubOps = new EditorEpubLibro(scanner, rutaCarpeta);
    }

    // Método estático para iniciar desde App.java
    public static void iniciar(Scanner scanner) {
        EditorXHTML editor = new EditorXHTML(scanner);
        // Verificar si hay algo con qué trabajar (XHTML o EPUB)
        if ((editor.archivosXHTML == null || editor.archivosXHTML.length == 0) && !editor.epubOps.hayEpub()) {
            System.err.println("No se encontraron archivos .xhtml ni un archivo .epub válido en la carpeta.");
            System.err.println("Volviendo al menú principal.");
            return;
        }
        editor.mostrarMenu();
    }

    // Carga/actualiza la lista de archivos .xhtml
    private void actualizarListaXHTML() {
        Path rutaText = Paths.get(this.rutaCarpeta, "OEBPS", "Text");
        File carpetaText = rutaText.toFile();
        if (carpetaText.exists() && carpetaText.isDirectory()) {
            this.archivosXHTML = carpetaText.listFiles((dir, name) -> name.toLowerCase().endsWith(".xhtml"));
            if (this.archivosXHTML != null) {
                Arrays.sort(this.archivosXHTML, Comparator.comparing(File::getName));
            } else {
                this.archivosXHTML = new File[0];
            }
        } else {
            this.archivosXHTML = new File[0];
        }
    }

    public void mostrarMenu() {
        while (true) {
            actualizarListaXHTML();
            boolean hayXHTML = (archivosXHTML != null && archivosXHTML.length > 0);
            boolean hayEPUB = epubOps.hayEpub();
            String estadoEPUB = (hayEPUB && epubOps.tieneCambios()) ? " (EPUB con cambios SIN GUARDAR)" : "";

            System.out.println("\n--- Editor XHTML / EPUB ---" + estadoEPUB);
            System.out.println("Carpeta: " + this.rutaCarpeta);

            // --- Opciones para XHTML ---
            if (hayXHTML) {
                System.out.println("\n--- Herramientas para Archivos .XHTML (" + archivosXHTML.length + ") ---");
                System.out.println("1. Estandarizar Títulos (.xhtml)");
                System.out.println("2. Renombrar Archivos (.xhtml)");
                System.out.println("3. Limpiar Encabezados Basura (.xhtml)");
                System.out.println("4. Validar Títulos (.xhtml)");
                System.out.println("5. Vincular Hoja de Estilo (CSS) a .xhtml");
                System.out.println("6. Generar Archivo de Índice (toc.xhtml)");
                System.out.println("7. CREAR Archivo EPUB...");
                System.out.println("8. Reparar Errores Comunes (.xhtml)");
                System.out.println("9. Analizar Faltantes");
            } else {
                System.out.println("\n[No se encontraron archivos .xhtml]");
            }

            // --- Opciones para EPUB ---
            if (hayEPUB) {
                System.out.println("\n--- Herramientas para el EPUB Cargado (" + epubOps.nombreEpub() + ") ---");
                System.out.println("11. Estandarizar Títulos (en EPUB)");
                System.out.println("12. Limpiar Encabezados Basura (en EPUB)");
                System.out.println("13. Validar Títulos (en EPUB)");
                System.out.println("14. Añadir/Actualizar Portada (en EPUB)");
                System.out.println("15. Añadir/Actualizar Sinopsis y Géneros (en EPUB)");
                System.out.println("19. GUARDAR CAMBIOS en el archivo EPUB");
            } else {
                System.out.println("\n[No se cargó ningún archivo .epub para editar]");
            }

            System.out.println("\n-----------------------------------");
            System.out.println("0. Volver al menú principal");
            System.out.print("Elige una herramienta: ");

            int opcion;
            try {
                opcion = scanner.nextInt();
            } catch (java.util.InputMismatchException e) {
                opcion = -1;
            }
            scanner.nextLine();

            EditorXhtmlArchivos xhtmlOps = new EditorXhtmlArchivos(scanner, rutaCarpeta, archivosXHTML);

            // --- Lógica del Switch ---
            switch (opcion) {
                case 1:
                    if (hayXHTML)
                        xhtmlOps.estandarizarTitulosXHTML();
                    else
                        System.out.println("Opción no disponible.");
                    break;
                case 2:
                    if (hayXHTML)
                        xhtmlOps.renombrarArchivosXHTML();
                    else
                        System.out.println("Opción no disponible.");
                    break;
                case 3:
                    if (hayXHTML)
                        xhtmlOps.limpiarEncabezadosXHTML();
                    else
                        System.out.println("Opción no disponible.");
                    break;
                case 4:
                    if (hayXHTML)
                        xhtmlOps.validarTitulosXHTML();
                    else
                        System.out.println("Opción no disponible.");
                    break;
                case 5:
                    if (hayXHTML)
                        xhtmlOps.vincularCssXHTML();
                    else
                        System.out.println("Opción no disponible.");
                    break;
                case 6:
                    if (hayXHTML)
                        xhtmlOps.generarTocXHTML();
                    else
                        System.out.println("Opción no disponible.");
                    break;
                case 7:
                    if (hayXHTML)
                        new CreadorEpub(scanner, rutaCarpeta, archivosXHTML).mostrarSubMenuCrearEpub();
                    else
                        sinOpcion();
                    break;
                case 8:
                    if (hayXHTML)
                        xhtmlOps.repararErroresComunes();
                    else
                        sinOpcion();
                    break;
                case 9:
                    if (hayXHTML)
                        xhtmlOps.analizarCapitulosFaltantes();
                    else
                        sinOpcion();
                    break;

                // Opciones EPUB (solo si hayEPUB)
                case 11:
                    if (hayEPUB)
                        epubOps.estandarizarTitulos();
                    else
                        System.out.println("Opción no disponible.");
                    break;
                case 12:
                    if (hayEPUB)
                        epubOps.limpiarEncabezados();
                    else
                        System.out.println("Opción no disponible.");
                    break;
                case 13:
                    if (hayEPUB)
                        epubOps.validarTitulos();
                    else
                        System.out.println("Opción no disponible.");
                    break;
                case 14:
                    if (hayEPUB)
                        epubOps.actualizarPortada();
                    else
                        System.out.println("Opción no disponible.");
                    break;
                case 15:
                    if (hayEPUB)
                        epubOps.actualizarMetadatos();
                    else
                        System.out.println("Opción no disponible.");
                    break;
                case 19:
                    if (hayEPUB)
                        epubOps.guardar();
                    else
                        System.out.println("Opción no disponible.");
                    break;
                case 0: // Salir
                    if (hayEPUB && epubOps.tieneCambios()) {
                        System.out.print(
                                "ADVERTENCIA: Tienes cambios sin guardar en el EPUB. ¿Salir de todos modos? (s/n): ");
                        if (!scanner.nextLine().equalsIgnoreCase("s")) {
                            continue;
                        }
                    }
                    return;
                default:
                    System.out.println("Opción no válida.");
            }
        }
    }

    private void sinOpcion() {
        System.out.println("Opción no disponible (verifica si hay archivos .xhtml o .epub cargado).");
    }
}
