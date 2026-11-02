package com.extractor.mi_extractor;

import java.util.function.Consumer;
import java.util.concurrent.TimeUnit;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
            EstructuraEpubInicializador.inicializar(rutaNovela);
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

        System.out.print("Introduce el número de capítulo con el que quieres que se guarde el primer archivo (ej. 1): ");
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
            System.out.println("\n>>> Navegando al primer capítulo: " + urlActual);
            // 1. Navegamos SOLO UNA VEZ a la URL inicial
            scraper.navegarA(urlActual);

            // 2. Usamos un bucle infinito que se romperá cuando ya no haya botón de 'Siguiente'
            while (true) {
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
                        System.out.println("\n Límite alcanzado (" + limiteCapitulos + " capítulos). Deteniendo el scraper...");
                        break;
                    }

                    numeroCapitulo++;

                    // 3. LA MAGIA: Le decimos a Selenium que busque el botón y haga clic
                    System.out.println("   Buscando el siguiente capítulo...");
                    boolean haySiguiente = scraper.irAlSiguienteCapitulo();

                    // Si Selenium no encuentra botón, rompemos el bucle porque llegamos al final
                    if (!haySiguiente) {
                        System.out.println("\n Fin de la novela alcanzado o no hay más capítulos publicados.");
                        break;
                    }

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

    /**
     * Filtra los capítulos según la opción del menú. Lógica pura (sin Selenium ni I/O),
     * extraída de {@code descargarWebMultiHilo} para poder testearla.
     *
     * <ul>
     *   <li>1: todos; 2: hasta {@code limite} (si {@code >0}); 3: solo el capítulo {@code limite};
     *   4: rango {@code [inicio, fin]}; 5: lista CSV en {@code listaStr}.</li>
     * </ul>
     */
    /** Escribe FALTANTES.txt con dos secciones: VIP (bloqueados) y fallos reales (no VIP). */
    private static void escribirReporteFaltantes(java.nio.file.Path pathBase, java.util.Set<Double> faltantes,
            java.util.Set<Double> vip) {
        try {
            StringBuilder sb = new StringBuilder();
            if (!vip.isEmpty()) {
                sb.append("Capitulos VIP (bloqueados, requieren desbloqueo en tu cuenta) (")
                        .append(vip.size()).append("):\n")
                        .append(CapitulosUtil.formatearListaCaps(vip)).append("\n\n");
            }
            if (!faltantes.isEmpty()) {
                sb.append("Capitulos NO descargados por error (NO VIP) (")
                        .append(faltantes.size()).append("):\n")
                        .append(CapitulosUtil.formatearListaCaps(faltantes)).append("\n");
            }
            java.nio.file.Files.writeString(pathBase.resolve("FALTANTES.txt"), sb.toString());
        } catch (Exception e) {
            System.err.println("No se pudo escribir FALTANTES.txt: " + e.getMessage());
        }
    }

    private static void ejecutarDescargaParalela(Map<Double, String> capitulosParaDescargar, String rutaDeGuardado,
            Scanner scanner) {
        if (capitulosParaDescargar.isEmpty()) {
            System.out.println("No hay capítulos para descargar.");
            return;
        }

        int totalCapitulos = capitulosParaDescargar.size();

        // Tú eliges cuántos navegadores en paralelo; se recorta al tope global (RAM).
        int maxNav = ConfigScraper.maxNavegadores();
        int porDefecto = Math.min(5, maxNav);
        System.out.print("¿Cuántos navegadores en paralelo? (1-" + maxNav + ", Enter = " + porDefecto + "): ");
        int numeroHilos = porDefecto;
        try {
            String linea = scanner.nextLine().trim();
            if (!linea.isEmpty()) {
                int elegido = Integer.parseInt(linea);
                if (elegido >= 1) {
                    numeroHilos = Math.min(elegido, maxNav);
                }
            }
        } catch (Exception e) {
            // Entrada no numérica: se queda el valor por defecto.
        }
        if (numeroHilos < 1) {
            numeroHilos = 1;
        }
        System.out.println("Usando " + numeroHilos + " navegadores (tope global: " + maxNav + ").");

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
        
        System.out.println("\n>>> Descargando " + totalCapitulos + " capítulos con " + numeroHilos + " navegadores...\n");

        // ADR-001: el bucle de descarga vive en MotorDescargaParalela (una sola copia,
        // compartida con la web). Aquí la CLI solo pinta una barra de progreso en vivo.
        final Object lockBarra = new Object();
        MotorDescargaParalela motor = new MotorDescargaParalela();
        MotorDescargaParalela.ResultadoLote resultado = motor.ejecutar(
                capitulosParaDescargar, rutaDeGuardado, numeroHilos,
                new MotorDescargaParalela.ProgresoDescarga() {
                    @Override
                    public void capituloIniciado(int hiloIdx, double numCap, int hechosEnHilo,
                            int totalEnHilo, int hechosGlobal, int totalGlobal) {
                        // La barra se repinta al COMPLETAR cada capítulo (capituloTerminado).
                    }

                    @Override
                    public void capituloTerminado(int hiloIdx, double numCap, int hechosEnHilo,
                            int totalEnHilo, int hechosGlobal, int totalGlobal) {
                        synchronized (lockBarra) {
                            imprimirBarraProgreso(hechosGlobal, totalGlobal);
                        }
                    }

                    @Override
                    public void hiloTerminado(int hiloIdx) {
                        // nada: el resumen final se imprime al terminar todo el lote.
                    }
                });
        System.out.println(); // cerrar la línea de la barra (venía con \r, sin salto)

        // Deja constancia en disco de VIP y fallidos (junto a los capítulos, en la carpeta de la novela).
        boolean hayPendientes = !resultado.vip.isEmpty() || !resultado.faltantes.isEmpty();
        if (hayPendientes) {
            escribirReporteFaltantes(Paths.get(rutaDeGuardado), resultado.faltantes, resultado.vip);
        }

        imprimirResumenDescarga(resultado, numeroHilos);

        if (hayPendientes) {
            System.out.println("   Detalle en: " + Paths.get(rutaDeGuardado, "FALTANTES.txt").toAbsolutePath());
            System.out.println("   (y la URL de cada fallo en logs/mi-extractor.log)");
        }
    }

    /**
     * Barra de progreso ASCII en una sola línea. Se reescribe sobre sí misma con '\r' (sin salto),
     * así que avanza en el mismo renglón. ASCII puro para que se vea bien en cualquier terminal.
     */
    private static void imprimirBarraProgreso(int hechos, int total) {
        final int ancho = 30;
        double ratio = (total <= 0) ? 1.0 : (double) hechos / total;
        if (ratio > 1.0) {
            ratio = 1.0;
        }
        int lleno = (int) Math.round(ratio * ancho);
        StringBuilder b = new StringBuilder();
        b.append("\r  Descargando  [");
        for (int i = 0; i < ancho; i++) {
            if (i < lleno - 1) {
                b.append('=');
            } else if (i == lleno - 1) {
                b.append('>');
            } else {
                b.append(' ');
            }
        }
        b.append("] ").append(String.format("%3d%%", (int) Math.round(ratio * 100)))
                .append("   ").append(hechos).append('/').append(total).append("   ");
        System.out.print(b);
        System.out.flush();
    }

    /** Resumen final de la descarga en una caja ASCII limpia. */
    private static void imprimirResumenDescarga(MotorDescargaParalela.ResultadoLote r, int numeroHilos) {
        System.out.println();
        System.out.println("  ------------------------------------------------");
        System.out.println("   Descarga finalizada");
        System.out.println("  ------------------------------------------------");
        System.out.println("   Guardados   : " + r.exitos + " / " + r.total);
        System.out.println("   VIP         : " + r.vip.size());
        System.out.println("   Fallidos    : " + r.faltantes.size());
        System.out.println("   Navegadores : " + numeroHilos);
        System.out.println("  ------------------------------------------------");
        if (!r.vip.isEmpty()) {
            System.out.println("   VIP (bloqueados, requieren desbloqueo en tu cuenta):");
            System.out.println("   " + CapitulosUtil.formatearListaCaps(r.vip));
        }
        if (!r.faltantes.isEmpty()) {
            System.out.println("   Fallidos (error, NO VIP):");
            System.out.println("   " + CapitulosUtil.formatearListaCaps(r.faltantes));
        }
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

    try {
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
    } finally {
        scraper.cerrar(); // SIEMPRE cerrar, aunque el navegador haya muerto a mitad del índice.
    }
    return mapaTotal;
}

    // ==============================================================
    // MÉTODO EXCLUSIVO PARA LA INTERFAZ WEB DE SPRING BOOT
    // ==============================================================
    public Path descargarWebMultiHilo(int opcion, String url, int hilos, int limite, int inicio, int fin,
            String listaStr, String rutaBaseStr, Consumer<String> progresoWeb) {
        try {
            Path pathBase = Paths.get(rutaBaseStr);
            Files.createDirectories(pathBase);
            EstructuraEpubInicializador.inicializar(pathBase);

            // CASO ESPECIAL: OPCIÓN 6 (Secuencial)
            if (opcion == 6) {
                return descargarSecuencialWeb(url, limite, rutaBaseStr, progresoWeb);
            }

            progresoWeb.accept("15% - 🔍 Analizando índice de la novela...");
            Map<Double, String> todosLosCapitulos = obtenerTodosLosCapitulos(url);

            // FILTRADO DE CAPÍTULOS (Lógica de Menú) — lógica pura extraída y testeable.
            Map<Double, String> filtrados = CapitulosUtil.filtrarCapitulos(todosLosCapitulos, opcion, limite, inicio, fin, listaStr);

            if (filtrados.isEmpty()) {
                progresoWeb.accept("ERROR: ❌ No se encontraron capítulos.");
                return null;
            }

            // --- PREPARACIÓN DEL MOTOR ---
            // Orden ascendente estable para que el troceo por hilos sea determinista.
            java.util.TreeMap<Double, String> ordenados = new java.util.TreeMap<>(filtrados);
            final int aDescargar = ordenados.size();
            int maxNav = ConfigScraper.maxNavegadores();
            if (hilos > maxNav) {
                progresoWeb.accept("Aviso: pediste " + hilos + " navegadores; limitado al tope global de " + maxNav + ".");
            }
            final int numHilosEfectivos = Math.min(Math.min(hilos, aDescargar), maxNav);

            // Estado por hilo para la UI (el JSON de progreso sale idéntico al de antes).
            String[] msgHilos = new String[numHilosEfectivos];
            int[] pctHilos = new int[numHilosEfectivos];
            for (int i = 0; i < numHilosEfectivos; i++) {
                msgHilos[i] = "Iniciando...";
                pctHilos[i] = 0;
            }

            // Arma y emite el JSON multihilo que ya consume el front (mismo formato).
            java.util.function.BiConsumer<Integer, Integer> emitirJson = (hechoGlobal, totalGlobal) -> {
                int gPct = 30 + (int) (((double) hechoGlobal / totalGlobal) * 60);
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

            // ADR-001: el bucle de descarga vive en MotorDescargaParalela (compartido con la CLI).
            MotorDescargaParalela motor = new MotorDescargaParalela();
            MotorDescargaParalela.ResultadoLote res = motor.ejecutar(
                    ordenados, rutaBaseStr, numHilosEfectivos,
                    new MotorDescargaParalela.ProgresoDescarga() {
                        @Override
                        public void capituloIniciado(int hiloIdx, double numCap, int hechosEnHilo,
                                int totalEnHilo, int hechosGlobal, int totalGlobal) {
                            msgHilos[hiloIdx] = "Cap #" + CapitulosUtil.formatearNumeroCap(numCap);
                            pctHilos[hiloIdx] = (totalEnHilo == 0) ? 0
                                    : (int) (((double) hechosEnHilo / totalEnHilo) * 100);
                            emitirJson.accept(hechosGlobal, totalGlobal);
                        }

                        @Override
                        public void capituloTerminado(int hiloIdx, double numCap, int hechosEnHilo,
                                int totalEnHilo, int hechosGlobal, int totalGlobal) {
                            pctHilos[hiloIdx] = (totalEnHilo == 0) ? 0
                                    : (int) (((double) hechosEnHilo / totalEnHilo) * 100);
                            emitirJson.accept(hechosGlobal, totalGlobal);
                        }

                        @Override
                        public void hiloTerminado(int hiloIdx) {
                            msgHilos[hiloIdx] = "LISTO ✅";
                            pctHilos[hiloIdx] = 100;
                            emitirJson.accept(aDescargar, aDescargar);
                        }
                    });

            // Reporte: VIP (bloqueados) y fallos reales, con números exactos.
            if (!res.faltantes.isEmpty() || !res.vip.isEmpty()) {
                escribirReporteFaltantes(pathBase, res.faltantes, res.vip);
                StringBuilder resumen = new StringBuilder("95% - Completado.");
                if (!res.vip.isEmpty()) {
                    resumen.append(" VIP (bloqueados): ").append(res.vip.size()).append('.');
                }
                if (!res.faltantes.isEmpty()) {
                    resumen.append(" Sin bajar: ").append(CapitulosUtil.formatearListaCaps(res.faltantes)).append('.');
                }
                resumen.append(" Ver FALTANTES.txt.");
                progresoWeb.accept(resumen.toString());
            }
            return pathBase;

        } catch (Exception e) {
            progresoWeb.accept("ERROR: " + e.getMessage());
            return null;
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