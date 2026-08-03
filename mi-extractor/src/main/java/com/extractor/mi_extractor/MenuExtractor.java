package com.extractor.mi_extractor;

import java.util.function.Consumer;
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
import java.util.TreeMap;
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
        System.out.println("6. Descargar SECUENCIALMENTE (Desde link de capítulo + Límite).");
        System.out.print("Elige una opción (1-6): ");
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
            } else if (opcion == 6) {
                descargarSecuencialmente(scanner, rutaNovelaStr);
            } else {
                System.out.println("Opción no válida.");
            }
        } catch (Exception e) {
            System.err.println("¡Error crítico en el menú!");
            e.printStackTrace();
        }

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

        Map<Double, String> todosLosCapitulos = obtenerTodosLosCapitulos(urlPortada);

        ejecutarDescargaParalela(todosLosCapitulos, rutaNovelaStr, scanner);
    }

    private static void buscarYDescargarCapitulo(Scanner scanner, String rutaNovelaStr) {
        System.out.println("Introduce la URL de la PÁGINA PRINCIPAL de la novela:");
        String urlIndice = scanner.nextLine();

        System.out.print("¿Qué número de capítulo quieres descargar? (puedes usar decimales para partes): ");
        double numeroCapituloBuscado = scanner.nextDouble();
        scanner.nextLine();

        Scraper scraper = new Scraper();
        IndiceExtractor extractorIndice = new IndiceExtractor();

        try {
            System.out.println("Analizando la página de índice...");
            scraper.navegarA(urlIndice);
            String htmlIndice = scraper.obtenerHtmlDeIndice();

            Map<Double, String> mapaDeCapitulos = extractorIndice.extraerEnlaces(htmlIndice, urlIndice);

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

        Map<Double, String> todosLosCapitulos = obtenerTodosLosCapitulos(urlPortada);

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

        Map<Double, String> capitulosFiltrados = new TreeMap<>();
        for (Map.Entry<Double, String> entry : todosLosCapitulos.entrySet()) {
            double num = entry.getKey();
            if (num >= inicio && num <= fin) {
                capitulosFiltrados.put(num, entry.getValue());
            }
        }

        ejecutarDescargaParalela(capitulosFiltrados, rutaNovelaStr, scanner);
    }

    private static void descargarListaEspecifica(Scanner scanner, String rutaNovelaStr) {
        System.out.println("Introduce la URL de la PORTADA de la novela:");
        String urlPortada = scanner.nextLine();

        System.out.println("Introduce los números de capítulo separados por comas (ej: 10, 2.1, 2.2):");
        String listaDeNumerosStr = scanner.nextLine();
        Map<Double, String> todosLosCapitulos = obtenerTodosLosCapitulos(urlPortada);

        if (todosLosCapitulos.isEmpty()) {
            System.out.println("Error: No se pudieron obtener capítulos de esa URL.");
            return;
        }
        Map<Double, String> capitulosParaDescargar = new TreeMap<>();

        try {
            String[] numeros = listaDeNumerosStr.split(",");
            for (String numeroStr : numeros) {
                double numBuscado = Double.parseDouble(numeroStr.trim());

                if (todosLosCapitulos.containsKey(numBuscado)) {
                    capitulosParaDescargar.put(numBuscado, todosLosCapitulos.get(numBuscado));
                } else {
                    System.err.println(
                            "Advertencia: El capítulo " + numBuscado + " no se encontró en el índice de la web.");
                }
            }
        } catch (NumberFormatException e) {
            System.err.println("Error: Ingresaste un valor que no es un número.");
            return;
        }

        ejecutarDescargaParalela(capitulosParaDescargar, rutaNovelaStr, scanner);
    }
    private static void descargarSecuencialmente(Scanner scanner, String rutaNovelaStr) {
        System.out.println("Introduce la URL del PRIMER CAPÍTULO a descargar:");
        String urlActual = scanner.nextLine();

        System.out.println(" ¿Cuántos capítulos deseas descargar a partir de este?");
        System.out.println("   (Ingrese 0 si desea descargar TODO hasta que no haya botón 'Siguiente'):");
        int limiteCapitulos = 0;
        try {
            limiteCapitulos = Integer.parseInt(scanner.nextLine());
        } catch (NumberFormatException e) {
            System.out.println("Entrada no válida. Se descargará todo por defecto.");
        }

        System.out
                .print("Introduce el número de capítulo con el que quieres que se guarde el primer archivo (ej. 1): ");
        int numeroCapitulo = 1;
        try {
            numeroCapitulo = Integer.parseInt(scanner.nextLine());
        } catch (NumberFormatException e) {
            System.out.println("Se iniciará desde el capítulo 1.");
        }

        Scraper scraper = new Scraper();
        ExtractorContenido extractor = new ExtractorContenido();
        int capitulosDescargados = 0;

        try {
            while (urlActual != null && !urlActual.isEmpty()) {
                System.out.println("\n>>> Navegando a: " + urlActual);
                scraper.navegarA(urlActual);
                String html = scraper.obtenerHtmlDePagina();
                SitioWebConfig config = scraper.getConfig();

                // Extraemos y guardamos el capítulo actual
                Capitulo capitulo = extractor.extraer(html, config);
                if (capitulo != null) {
                    EscritorArchivo escritor = new EscritorArchivo();
                    escritor.guardarCapitulo(capitulo, rutaNovelaStr, numeroCapitulo);
                    capitulosDescargados++;
                    System.out.println(" Capítulo " + numeroCapitulo + " guardado con éxito.");

                    // Evaluamos si ya llegamos al límite establecido por el usuario
                    if (limiteCapitulos > 0 && capitulosDescargados >= limiteCapitulos) {
                        System.out.println(
                                "\n Límite alcanzado (" + limiteCapitulos + " capítulos). Deteniendo el scraper...");
                        break;
                    }

                    // Preparamos para el siguiente ciclo
                    numeroCapitulo++;

                    // NOTA: Asegúrate de que tu ExtractorContenido tenga un método para obtener
                    // el link del SIGUIENTE CAPÍTULO. Si se llama diferente, ajusta la siguiente
                    // línea:
                    urlActual = extractor.obtenerEnlaceSiguienteCapitulo(html, urlActual, config);

                } else {
                    System.err.println("No se pudo extraer el contenido. Abortando descarga secuencial.");
                    break;
                }
            }
        } catch (Exception e) {
            System.err.println("Ocurrió un error en la descarga secuencial: " + e.getMessage());
        } finally {
            scraper.cerrar();
            System.out.println("\n--- Descarga Secuencial Finalizada ---");
            System.out.println("Total de capítulos descargados: " + capitulosDescargados);
        }
    }

   private static Capitulo procesarUnCapitulo(Scraper scraper, String url, String rutaDeGuardado, double numeroCapitulo)
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

    private static double extraerNumeroDeTitulo(String titulo) {
        if (titulo == null)
            return 0.0;
        Pattern pattern = Pattern.compile("\\d+(\\.\\d+)?");
        Matcher matcher = pattern.matcher(titulo);
        if (matcher.find()) {
            try {
                return Double.parseDouble(matcher.group());
            } catch (NumberFormatException e) {
                return 0.0;
            }
        }
        return 0.0;
    }

    private static void ejecutarDescargaParalela(Map<Double, String> capitulosParaDescargar, String rutaDeGuardado,
            Scanner scanner) {
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
        
        // --- NUEVO: LISTA SEGURA PARA RECOLECTAR ERRORES ORDENADOS ---
        java.util.Set<Double> capitulosFallidos = new java.util.concurrent.ConcurrentSkipListSet<>();

        System.out.println("\n>>> INICIANDO DESCARGA CON " + numeroHilos + " NAVEGADORES...");

        List<Map.Entry<Double, String>> listaTotal = new ArrayList<>(capitulosParaDescargar.entrySet());
        int tamanoLote = (int) Math.ceil((double) totalCapitulos / numeroHilos);

        for (int i = 0; i < numeroHilos; i++) {
            final int inicio = i * tamanoLote;
            if (inicio >= totalCapitulos)
                break;

            final int fin = Math.min(inicio + tamanoLote, totalCapitulos);
            List<Map.Entry<Double, String>> subLista = listaTotal.subList(inicio, fin);
            
            executor.submit(() -> {
                Scraper scraperHilo = new Scraper();
                int capitulosDescargadosPorEsteHilo = 0;

                try {
                    for (Map.Entry<Double, String> entrada : subLista) {
                        double numCap = entrada.getKey();
                        String urlCap = entrada.getValue();
                        
                        boolean exito = false; // Bandera para saber si se logró guardar
                        
                        try {
                            System.out.println("[Hilo " + Thread.currentThread().getId() + "] Bajando Capitulo #" + numCap);

                            scraperHilo.navegarA(urlCap);
                            String html = scraperHilo.obtenerHtmlDePagina();
                            SitioWebConfig configDetectada = scraperHilo.getConfig();
                            ExtractorContenido extractor = new ExtractorContenido();
                            Capitulo capitulo = extractor.extraer(html, configDetectada);

                            if (capitulo != null) {
                                EscritorArchivo escritor = new EscritorArchivo();
                                escritor.guardarCapitulo(capitulo, rutaDeGuardado, numCap);
                                contadorExitos.incrementAndGet();
                                exito = true; // Todo salió perfecto
                            }
                        } catch (Exception e) {
                            System.err.println("X Error en Capitulo #" + numCap + ": " + e.getMessage());
                        }
                        
                        // Si hubo excepción o si el capítulo fue NULL, anotamos el error
                        if (!exito) {
                            capitulosFallidos.add(numCap);
                        }
                        capitulosDescargadosPorEsteHilo++;
                        
                        if (capitulosDescargadosPorEsteHilo % 100 == 0) {
                            System.out.println("[Hilo " + Thread.currentThread().getId() + "] Refrescando navegador para liberar RAM...");
                            scraperHilo.cerrar();
                            scraperHilo = new Scraper();
                        }
                        // ----------------------------------------------------
                    }
                } finally {
                    scraperHilo.cerrar();
                }
            });
        }

        executor.shutdown();
        try {
            if (!executor.awaitTermination(4, TimeUnit.HOURS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        System.out.println("\n--- Proceso finalizado ---");
        System.out.println("Descargados correctamente: " + contadorExitos.get() + "/" + totalCapitulos);
        System.out.println("Cantidad de hilos: " + numeroHilos);
        
        // --- REPORTE DE ERRORES FORMATADO ---
        if (!capitulosFallidos.isEmpty()) {
            System.out.println("\n ATENCIÓN: Hubo errores al descargar " + capitulosFallidos.size() + " capítulos.");
            System.out.println("\nCapitulos: ");
            
            StringBuilder sbErrores = new StringBuilder();
            for (Double cap : capitulosFallidos) {
                if (sbErrores.length() > 0) {
                    sbErrores.append(", ");
                }
                // Quitamos el ".0" para que se vea limpio (ej: 1878 en lugar de 1878.0)
                if (cap % 1 == 0) {
                    sbErrores.append(cap.intValue());
                } else {
                    sbErrores.append(cap);
                }
            }
            System.out.println(sbErrores.toString());
            System.out.println("\n");
        }

        System.out.println("--- Fin del Módulo Extractor ---");
    }

    private static double calcularConstanteRealista(int hilos) {
        if (hilos <= 2)
            return 2.0;
        if (hilos <= 5)
            return 2.2; // Óptimo (Tu prueba de 500 caps)
        if (hilos == 6)
            return 2.8;
        if (hilos == 7)
            return 3.5;
        if (hilos == 8)
            return 4.2;
        if (hilos == 9)
            return 4.9;
        return 5.5;
    }

    private static Map<Double, String> obtenerTodosLosCapitulos(String urlPortada) {
    System.out.println("Obteniendo lista de capítulos...");
    Map<Double, String> mapaTotal = new TreeMap<>();

    Scraper scraper = new Scraper();
    IndiceExtractor extractor = new IndiceExtractor();

    String urlActual = urlPortada;
    int pagina = 1;

    while (urlActual != null) {
        System.out.println(">>> Analizando índice - Página " + pagina + "...");
        scraper.navegarA(urlActual);

        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
        }

        String htmlIndice = scraper.obtenerHtmlDeIndice();
        if (htmlIndice == null)
            break;

        Map<Double, String> capitulosPagina = extractor.extraerEnlaces(htmlIndice, urlActual);

        if (capitulosPagina.isEmpty()) {
            System.out.println("⚠ No se encontraron capítulos en la página " + pagina + ".");
            if (pagina == 1)
                break;
        } else {
            mapaTotal.putAll(capitulosPagina);
            System.out.println("    -> Encontrados " + capitulosPagina.size() + " caps. (Total acumulado: "
                    + mapaTotal.size() + ")");
        }

        String urlSiguiente = extractor.obtenerSiguientePagina(htmlIndice, urlActual);

        if (urlSiguiente != null && !urlSiguiente.equals(urlActual)) {
            System.out.println(">>> Detectada siguiente página: " + urlSiguiente);
            urlActual = urlSiguiente;
            pagina++;
        } else {
            System.out.println(">>> Fin del índice (No se detectaron más páginas).");
            urlActual = null;
        }
    }

    scraper.cerrar();
    return mapaTotal;
}

    // ==============================================================
    // MÉTODO EXCLUSIVO PARA LA INTERFAZ WEB DE SPRING BOOT
    // ==============================================================
    public Path descargarWebMultiHilo(int opcion, String url, int hilos, int limite, int inicio, int fin,
            String listaStr, String rutaBaseStr, Consumer<String> progresoWeb) {
        ExecutorService executor = null; // Definido fuera para el cierre de emergencia
        try {
            Path pathBase = Paths.get(rutaBaseStr);
            Files.createDirectories(pathBase);
            inicializarEstructuraEPUB(pathBase);

            // CASO ESPECIAL: OPCIÓN 6 (Secuencial)
            if (opcion == 6) {
                return descargarSecuencialWeb(url, limite, rutaBaseStr, progresoWeb);
            }

            progresoWeb.accept("15% - 🔍 Analizando índice de la novela...");
            Map<Double, String> todosLosCapitulos = obtenerTodosLosCapitulos(url);
            Map<Double, String> filtrados = new HashMap<>();

            // FILTRADO DE CAPÍTULOS (Lógica de Menú)
            switch (opcion) {
                case 1:
                case 2:
                    int max = (opcion == 2 && limite > 0) ? limite : Integer.MAX_VALUE;
                    for (Double k : todosLosCapitulos.keySet())
                        if (k <= max)
                            filtrados.put(k, todosLosCapitulos.get(k));
                    break;
                case 4:
                    for (Double k : todosLosCapitulos.keySet())
                        if (k >= inicio && k <= fin)
                            filtrados.put(k, todosLosCapitulos.get(k));
                    break;
                case 5:
                    String[] nums = listaStr.split(",");
                    for (String n : nums) {
                        try {
                            double nb = Double.parseDouble(n.trim());
                            if (todosLosCapitulos.containsKey(nb))
                                filtrados.put(nb, todosLosCapitulos.get(nb));
                        } catch (Exception e) {
                        }
                    }
                    break;
                case 3:
                    double dLimite = (double) limite;
                    if (todosLosCapitulos.containsKey(dLimite))
                        filtrados.put(dLimite, todosLosCapitulos.get(dLimite));
                    break;
            }

            if (filtrados.isEmpty()) {
                progresoWeb.accept("ERROR: ❌ No se encontraron capítulos.");
                return null;
            }

            // --- PREPARACIÓN DEL MOTOR ---
            List<Double> keys = new ArrayList<>(filtrados.keySet());
            Collections.sort(keys);
            int aDescargar = keys.size();
            int numHilosEfectivos = Math.min(hilos, aDescargar);

            String[] msgHilos = new String[numHilosEfectivos];
            int[] pctHilos = new int[numHilosEfectivos];
            AtomicInteger globales = new AtomicInteger(0);
            for (int i = 0; i < numHilosEfectivos; i++) {
                msgHilos[i] = "Iniciando...";
                pctHilos[i] = 0;
            }

            // Función de reporte JSON para la web
            Runnable updateUI = () -> {
                int gPct = 30 + (int) (((double) globales.get() / aDescargar) * 60);
                StringBuilder sb = new StringBuilder("{ \"tipo\": \"MULTIHILO\", \"globalPct\": ").append(gPct)
                        .append(", \"hilos\": [");
                for (int i = 0; i < numHilosEfectivos; i++) {
                    sb.append("{\"id\": ").append(i + 1).append(", \"pct\": ").append(pctHilos[i])
                            .append(", \"msg\": \"").append(msgHilos[i]).append("\"}");
                    if (i < numHilosEfectivos - 1)
                        sb.append(", ");
                }
                sb.append("]}");
                progresoWeb.accept(sb.toString());
            };

            // INICIO DEL EXECUTOR
            executor = Executors.newFixedThreadPool(numHilosEfectivos);
            int chunkSize = (int) Math.ceil((double) aDescargar / numHilosEfectivos);

            for (int i = 0; i < numHilosEfectivos; i++) {
                final int idH = i;
                int start = i * chunkSize;
                int end = Math.min(start + chunkSize, aDescargar);
                if (start >= aDescargar)
                    break;
                List<Double> miLote = keys.subList(start, end);

                executor.submit(() -> {
                    Scraper sH = new Scraper();
                    ExtractorContenido eH = new ExtractorContenido();
                    EscritorArchivo wH = new EscritorArchivo();
                    int count = 0;
                    try {
                        for (Double nCap : miLote) {
                            msgHilos[idH] = "Cap #" + nCap;
                            pctHilos[idH] = (int) (((double) count / miLote.size()) * 100);
                            updateUI.run();

                            sH.navegarA(filtrados.get(nCap));
                            Capitulo c = eH.extraer(sH.obtenerHtmlDePagina(), sH.getConfig());
                            if (c != null)
                                wH.guardarCapitulo(c, rutaBaseStr, nCap);

                            count++;
                            globales.incrementAndGet();
                            updateUI.run();
                        }
                        msgHilos[idH] = "LISTO ✅";
                        pctHilos[idH] = 100;
                        updateUI.run();
                    } catch (Exception ex) {
                        msgHilos[idH] = "ERROR ❌";
                    } finally {
                        sH.cerrar();
                    }
                });
            }

            executor.shutdown();
            executor.awaitTermination(5, TimeUnit.HOURS);
            return pathBase;

        } catch (Exception e) {
            progresoWeb.accept("ERROR: " + e.getMessage());
            return null;
        } finally {
            // CIERRE DE EMERGENCIA: Si el executor quedó vivo por un error, lo matamos aquí
            if (executor != null && !executor.isShutdown()) {
                executor.shutdownNow();
            }
        }
    }

    /**
     * MÉTODO SECUENCIAL (OPCIÓN 6)
     * Corregido el error de escritura y el typo en progresoWeb.
     */
    private Path descargarSecuencialWeb(String url, int limite, String rutaBase, Consumer<String> progresoWeb) {
        Scraper s = new Scraper();
        ExtractorContenido e = new ExtractorContenido();
        EscritorArchivo w = new EscritorArchivo();
        try {
            String urlAct = url;
            int count = 0;
            int max = (limite > 0) ? limite : Integer.MAX_VALUE;

            while (urlAct != null && count < max) {
                progresoWeb.accept("⚙️ Descargando Cap " + (count + 1));
                s.navegarA(urlAct);
                String html = s.obtenerHtmlDePagina();
                Capitulo cap = e.extraer(html, s.getConfig());

                if (cap == null)
                    break;

                // Bloque de escritura seguro
                try {
                    w.guardarCapitulo(cap, rutaBase, count + 1);
                } catch (Exception writeEx) {
                    System.err.println("Error escribiendo archivo: " + writeEx.getMessage());
                }

                count++;
                urlAct = e.obtenerEnlaceSiguienteCapitulo(html, urlAct, s.getConfig());
            }
            return Paths.get(rutaBase);
        } catch (Exception ex) {
            progresoWeb.accept("ERROR en descarga secuencial: " + ex.getMessage());
            return null;
        } finally {
            s.cerrar();
        }
    }
}