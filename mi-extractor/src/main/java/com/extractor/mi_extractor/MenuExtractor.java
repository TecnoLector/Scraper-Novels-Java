package com.extractor.mi_extractor;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.StandardCopyOption;
import java.nio.charset.StandardCharsets;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.stream.Stream;

public class MenuExtractor {

    public static void iniciar(Scanner scanner) {
        long tiempoInicio = System.currentTimeMillis();

        System.out.println("\n--- Extractor de Capítulos ---");
        System.out.println("Introduce la ruta de la carpeta donde se guardará la novela:");
        String rutaDeGuardado = scanner.nextLine();
        Path rutaBase = Paths.get(rutaDeGuardado);

        try {
            Files.createDirectories(rutaBase);

        } catch (IOException e) {
            System.err.println("Error CRÍTICO al crear carpeta o inicializar estructura: " + e.getMessage());
            return;
        }

        System.out.println("Introduce el NOMBRE ESPECÍFICO de esta novela (se creará una carpeta con este nombre):");
        String nombreNovela = scanner.nextLine();
        if (nombreNovela.isBlank()) {
            System.err.println("Error: El nombre de la novela no puede estar vacío.");
            return;
        }

        Path rutaNovela = rutaBase.resolve(nombreNovela);

        try {
            Files.createDirectories(rutaNovela);
            System.out.println("Usando carpeta de novela: " + rutaNovela);
            inicializarEstructuraEPUB(rutaNovela);
        } catch (IOException e) {
            System.err.println("Error CRÍTICO al crear carpeta de novela o inicializar estructura: " + e.getMessage());
            e.printStackTrace();
            return;
        }

        System.out.println("\n¿Qué quieres hacer?");
        System.out.println("1. Descargar TODA la Novela.");
        System.out.println("2. Descargar HASTA un número de capítulo.");
        System.out.println("3. BUSCAR un capítulo por su NÚMERO.");
        System.out.println("4. Descargar un RANGO de capítulos.");
        System.out.println("5. Descargar una LISTA específica de capítulos.");
        System.out.print("Elige una opción (1-5): ");
        int opcion;
        try {
            opcion = scanner.nextInt();
        } catch (java.util.InputMismatchException e) {
            opcion = -1;
        }
        scanner.nextLine();

        try {
            String rutaNovelaStr = rutaNovela.toString();
            if (opcion == 1 || opcion == 2) {
                descargarTodaLaNovela(scanner, rutaNovelaStr);
            } else if (opcion == 3) {
                buscarYDescargarCapitulo(scanner, rutaNovelaStr);
            } else if (opcion == 4) {
                descargarRangoDeCapitulos(scanner, rutaNovelaStr);
            } else if (opcion == 5) {
                descargarListaEspecifica(scanner, rutaNovelaStr);
            } else {
                System.out.println("Opción no válida.");
            }
        } catch (Exception e) {
            /* ... */ }

        long tiempoFin = System.currentTimeMillis();
        long tiempoTotalMillis = tiempoFin - tiempoInicio;
        long minutos = TimeUnit.MILLISECONDS.toMinutes(tiempoTotalMillis);
        long segundos = TimeUnit.MILLISECONDS.toSeconds(tiempoTotalMillis) - TimeUnit.MINUTES.toSeconds(minutos);

        System.out.println("Tiempo de ejecución: " + minutos + " min " + segundos + " s.");
        System.out.println("\n--- Fin del Módulo Extractor ---");
    }

    private static void inicializarEstructuraEPUB(Path rutaBase) throws IOException {
        Path rutaMimetype = rutaBase.resolve("mimetype");

        if (Files.notExists(rutaMimetype)) {
            System.out.println("Detectada carpeta nueva o incompleta. Creando estructura EPUB base...");

            Files.createDirectories(rutaBase.resolve("META-INF"));
            Path rutaOEBPS = rutaBase.resolve("OEBPS");
            Files.createDirectories(rutaOEBPS);
            Files.createDirectories(rutaOEBPS.resolve("Text"));
            Path rutaStyles = rutaOEBPS.resolve("Styles");
            Path rutaFonts = rutaOEBPS.resolve("Fonts");
            Files.createDirectories(rutaStyles);
            Files.createDirectories(rutaFonts);
            Files.createDirectories(rutaOEBPS.resolve("Images"));
            Files.writeString(rutaMimetype, "application/epub+zip", StandardCharsets.US_ASCII);

            copiarRecursoDesdeJar("/recursos/stylesheet.css", rutaStyles.resolve("stylesheet.css"));

            System.out.println("Buscando y copiando fuentes (.ttf, .otf)...");
            try {
                String rutaJarFonts = "/recursos/fonts";
                URL urlFonts = MenuExtractor.class.getResource(rutaJarFonts);

                if (urlFonts != null) {
                    URI uriFonts = urlFonts.toURI();
                    Path pathFonts;
                    FileSystem fileSystem = null;

                    if (uriFonts.getScheme().equals("jar")) {
                        try {
                            fileSystem = FileSystems.getFileSystem(uriFonts);
                        } catch (java.nio.file.FileSystemNotFoundException e) {
                            fileSystem = FileSystems.newFileSystem(uriFonts, Collections.emptyMap());
                        }
                        pathFonts = fileSystem.getPath(rutaJarFonts);
                    } else {
                        pathFonts = Paths.get(uriFonts);
                    }
                    PathMatcher fontMatcher = FileSystems.getDefault().getPathMatcher("glob:**.{ttf,otf}");
                    try (Stream<Path> paths = Files.walk(pathFonts, 1)) {
                        paths.filter(Files::isRegularFile)
                                .filter(fontMatcher::matches)
                                .forEach(fontPath -> {
                                    String nombreFuente = fontPath.getFileName().toString();
                                    String rutaCompletaEnJar = rutaJarFonts + "/" + nombreFuente;
                                    Path destinoFuente = rutaFonts.resolve(nombreFuente);
                                    copiarRecursoDesdeJar(rutaCompletaEnJar, destinoFuente);
                                });
                    }

                    // Cerrar el sistema de archivos del JAR si lo abrimos nosotros
                    // ¡OJO! No cerrar si lo obtuvimos con getFileSystem, podría ser usado por otros
                    // if (fileSystem != null && uriFonts.getScheme().equals("jar") &&
                    // !FileSystems.getFileSystem(uriFonts).equals(fileSystem)) {
                    // fileSystem.close(); // Comentado por precaución, el cierre automático a veces
                    // da problemas
                    // }

                } else {
                    System.err.println("ADVERTENCIA: No se encontró la carpeta de recursos '/recursos/fonts'.");
                }

            } catch (URISyntaxException | IOException e) {
                System.err.println("Error al intentar listar o copiar fuentes: " + e.getMessage());
                e.printStackTrace(); // Mostrar más detalles del error
            }

            System.out.println("¡Estructura EPUB base creada con éxito!");
        } else {
            System.out.println("Estructura EPUB detectada. Añadiendo capítulos...");
        }
    }

    /**
     * Copia un recurso desde el classpath a una ruta de destino en el disco.
     */
    private static void copiarRecursoDesdeJar(String rutaEnJar, Path rutaDestino) {
        try (InputStream is = MenuExtractor.class.getResourceAsStream(rutaEnJar)) {
            if (is == null) {
                System.err.println("ADVERTENCIA: Recurso no encontrado en el JAR: " + rutaEnJar);
                return;
            }
            Files.createDirectories(rutaDestino.getParent());
            Files.copy(is, rutaDestino, StandardCopyOption.REPLACE_EXISTING);
            System.out.println("Recurso copiado a: " + rutaDestino);
        } catch (IOException | NullPointerException e) {
            System.err.println("Error al copiar recurso '" + rutaEnJar + "': " + e.getMessage());
        }
    }

    private static void descargarTodaLaNovela(Scanner scanner, String rutaNovelaStr) {
        System.out.println("Introduce la URL de la PORTADA de la novela:");
        String urlPortada = scanner.nextLine();

        Map<Integer, String> todosLosCapitulos = obtenerTodosLosCapitulos(urlPortada);

        ejecutarDescargaParalela(todosLosCapitulos, rutaNovelaStr, scanner);
    }

    private static void buscarYDescargarCapitulo(Scanner scanner, String rutaNovelaStr) {
        System.out.println("Introduce la URL de la PÁGINA PRINCIPAL de la novela:");
        String urlIndice = scanner.nextLine();

        System.out.print("¿Qué número de capítulo quieres descargar?: ");
        int numeroCapituloBuscado = scanner.nextInt();
        scanner.nextLine();

        Scraper scraper = new Scraper();
        IndiceExtractor extractorIndice = new IndiceExtractor();

        try {
            System.out.println("Analizando la página de índice...");
            scraper.navegarA(urlIndice);
            String htmlIndice = scraper.obtenerHtmlDeIndice();

            Map<Integer, String> mapaDeCapitulos = extractorIndice.extraerEnlaces(htmlIndice, urlIndice);

            if (mapaDeCapitulos.containsKey(numeroCapituloBuscado)) {
                String urlCapitulo = mapaDeCapitulos.get(numeroCapituloBuscado);
                System.out.println("¡Enlace encontrado! URL: " + urlCapitulo);
                procesarUnCapitulo(scraper, urlCapitulo, rutaNovelaStr, numeroCapituloBuscado);
            } else {
                System.err.println("Error: No se pudo encontrar el capítulo número " + numeroCapituloBuscado + ".");
            }
        } catch (Exception e) {
            System.err.println("Ocurrió un error al buscar el capítulo: " + e.getMessage());
            e.printStackTrace();
        } finally {
            scraper.cerrar();
        }
    }

    private static void descargarRangoDeCapitulos(Scanner scanner, String rutaNovelaStr) {
        System.out.println("Introduce la URL de la PORTADA de la novela:");
        String urlPortada = scanner.nextLine();

        Map<Integer, String> todosLosCapitulos = obtenerTodosLosCapitulos(urlPortada);

        if (todosLosCapitulos.isEmpty()) {
            System.out.println("No se encontraron capítulos. Revisa la URL.");
            return;
        }

        System.out.print("\n --------------------");
        System.out.print("Desde Capitulo #: ");
        int inicio = scanner.nextInt();
        System.out.print("Hasta Capitulo #: ");
        int fin = scanner.nextInt();
        scanner.nextLine();

        Map<Integer, String> capitulosFiltrados = new HashMap<>();
        for (int i = inicio; i <= fin; i++) {
            if (todosLosCapitulos.containsKey(i)) {
                capitulosFiltrados.put(i, todosLosCapitulos.get(i));
            }
        }

        ejecutarDescargaParalela(capitulosFiltrados, rutaNovelaStr, scanner);
    }

    private static void descargarListaEspecifica(Scanner scanner, String rutaNovelaStr) {
    System.out.println("Introduce la URL de la PORTADA de la novela:");
    String urlPortada = scanner.nextLine();

    System.out.println("Introduce los números de capítulo separados por comas (ej: 10, 25, 100):");
    String listaDeNumerosStr = scanner.nextLine();
    Map<Integer, String> todosLosCapitulos = obtenerTodosLosCapitulos(urlPortada);

    if (todosLosCapitulos.isEmpty()) {
        System.out.println("Error: No se pudieron obtener capítulos de esa URL.");
        return;
    }
    Map<Integer, String> capitulosParaDescargar = new HashMap<>();
    
    try {
        String[] numeros = listaDeNumerosStr.split(",");
        for (String numeroStr : numeros) {
            int numBuscado = Integer.parseInt(numeroStr.trim());
            
            if (todosLosCapitulos.containsKey(numBuscado)) {
                capitulosParaDescargar.put(numBuscado, todosLosCapitulos.get(numBuscado));
            } else {
                System.err.println("Advertencia: El capítulo " + numBuscado + " no se encontró en el índice de la web.");
            }
        }
    } catch (NumberFormatException e) {
        System.err.println("Error: Ingresaste un valor que no es un número.");
        return;
    }

    ejecutarDescargaParalela(capitulosParaDescargar, rutaNovelaStr, scanner);
}

    private static Capitulo procesarUnCapitulo(Scraper scraper, String url, String rutaDeGuardado, int numeroCapitulo)
            throws Exception {
        if (url != null)
            scraper.navegarA(url);

        System.out.println("\n--- Procesando Capítulo #" + numeroCapitulo + " ---");
        String html = scraper.obtenerHtmlDePagina();

        SitioWebConfig config = scraper.getConfig();
        ExtractorContenido extractor = new ExtractorContenido();
        EscritorArchivo escritor = new EscritorArchivo();

        Capitulo capitulo = extractor.extraer(html, config);
        if (capitulo != null) {
            escritor.guardarCapitulo(capitulo, rutaDeGuardado, numeroCapitulo);
            return capitulo;
        } else {
            System.err.println("No se pudo extraer el contenido del capítulo. Abortando.");
            return null;
        }
    }

    private static int extraerNumeroDeTitulo(String titulo) {
        if (titulo == null)
            return 0;
        Pattern pattern = Pattern.compile("\\d+");
        Matcher matcher = pattern.matcher(titulo);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group());
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }

    private static void ejecutarDescargaParalela(Map<Integer, String> capitulosParaDescargar, String rutaDeGuardado, Scanner scanner) {
        if (capitulosParaDescargar.isEmpty()) {
            System.out.println("No hay capítulos para descargar.");
            return;
        }

        int totalCapitulos = capitulosParaDescargar.size();
        int numeroHilos = 5; 
        double constanteVelocidad = calcularConstanteRealista(numeroHilos);
        double tiempoTotalSegundos = (totalCapitulos * constanteVelocidad) / numeroHilos;
        long minutosEst = (long) (tiempoTotalSegundos / 60);
        long segundosEst = (long) (tiempoTotalSegundos % 60);

        System.out.println("\n------------------------------------------------");
        System.out.println(" Resumen de la Extraccion");
        System.out.println("------------------------------------------------");
        System.out.println(" Total Capítulos: " + totalCapitulos);
        System.out.println(" Hilos activos:   " + numeroHilos + "");
        System.out.println(" Factor de carga: " + constanteVelocidad + "s por hilo");
        System.out.println(" TIEMPO ESTIMADO: ~" + minutosEst + " min " + segundosEst + " s.");
        System.out.println("------------------------------------------------");
        
        System.out.print("¿Deseas continuar? (Y/N): ");
        String confirmacion = scanner.nextLine().trim().toUpperCase();

        if (!confirmacion.equals("Y")) {
            System.out.println("Operación cancelada por el usuario.");
            return;
        }
        ExecutorService executor = Executors.newFixedThreadPool(numeroHilos);
        AtomicInteger contadorExitos = new AtomicInteger(0);

        System.out.println("\n>>> INICIANDO DESCARGA CON " + numeroHilos + " NAVEGADORES...");

        List<Map.Entry<Integer, String>> listaTotal = new ArrayList<>(capitulosParaDescargar.entrySet());

        int tamanoLote = (int) Math.ceil((double) totalCapitulos / numeroHilos);

        for (int i = 0; i < numeroHilos; i++) {
            final int inicio = i * tamanoLote;
            if (inicio >= totalCapitulos) break;
            
            final int fin = Math.min(inicio + tamanoLote, totalCapitulos);
            List<Map.Entry<Integer, String>> subLista = listaTotal.subList(inicio, fin);
            executor.submit(() -> {
                Scraper scraperHilo = new Scraper(); 
                try {
                    for (Map.Entry<Integer, String> entrada : subLista) {
                        int numCap = entrada.getKey();
                        String urlCap = entrada.getValue();
                        try {
                            System.out.println("[Hilo " + Thread.currentThread().getId() + "] Bajando Cap #" + numCap);
                            
                            scraperHilo.navegarA(urlCap);
                            String html = scraperHilo.obtenerHtmlDePagina();
                            SitioWebConfig configDetectada = scraperHilo.getConfig();
                            ExtractorContenido extractor = new ExtractorContenido();
                            Capitulo capitulo = extractor.extraer(html, configDetectada);

                            if (capitulo != null) {
                                EscritorArchivo escritor = new EscritorArchivo();
                                escritor.guardarCapitulo(capitulo, rutaDeGuardado, numCap);
                                contadorExitos.incrementAndGet();
                            }
                        } catch (Exception e) {
                            System.err.println("X Error en Cap #" + numCap + ": " + e.getMessage());
                        }
                    }
                } finally {
                    scraperHilo.cerrar(); 
                }
            });
        }

        executor.shutdown();
        try {
            executor.awaitTermination(3, TimeUnit.HOURS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        
        System.out.println("\n--- Proceso finalizado ---");
        System.out.println("Descargados correctamente: " + contadorExitos.get() + "/" + totalCapitulos);
        System.out.println("Cantidad de hilos: " +numeroHilos);
    }

    private static double calcularConstanteRealista(int hilos) {
        if (hilos <= 2) return 2.0;
        if (hilos <= 5) return 2.2; // Óptimo (Tu prueba de 500 caps)
        if (hilos == 6) return 2.8;
        if (hilos == 7) return 3.5;
        if (hilos == 8) return 4.2;
        if (hilos == 9) return 4.9;
        return 5.5;
    }

    private static Map<Integer, String> obtenerTodosLosCapitulos(String urlPortada) {
        System.out.println("Obteniendo lista de capítulos desde: " + urlPortada);

        Scraper scraper = new Scraper();
        scraper.navegarA(urlPortada);
        String htmlIndice = scraper.obtenerHtmlDeIndice();
        
        scraper.cerrar();

        if (htmlIndice != null && !htmlIndice.isEmpty()) {
            IndiceExtractor extractor = new IndiceExtractor();
            return extractor.extraerEnlaces(htmlIndice, urlPortada);
        } else {
            return new HashMap<>(); // Retornamos mapa vacío si falló
        }
    }
}