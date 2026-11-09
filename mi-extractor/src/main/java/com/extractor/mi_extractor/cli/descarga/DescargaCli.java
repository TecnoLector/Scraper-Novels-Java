package com.extractor.mi_extractor.cli.descarga;
import com.extractor.mi_extractor.scraping.util.ConfigScraper;
import com.extractor.mi_extractor.scraping.model.SitioWebConfig;
import com.extractor.mi_extractor.scraping.model.Capitulo;
import com.extractor.mi_extractor.scraping.service.*;
import com.extractor.mi_extractor.epub.service.EstructuraEpubInicializador;
import com.extractor.mi_extractor.common.ReporteFaltantes;
import com.extractor.mi_extractor.common.CapitulosUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Scanner;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

/**
 * Adaptador CLI de descarga: menú por consola y flujos de descarga (toda la novela, rango,
 * lista, un capítulo, secuencial) con el motor paralelo y la barra de progreso. Extraído de
 * {@link DescargaWebService} sin cambios de comportamiento. La parte web vive en DescargaWebService;
 * ambos comparten CatalogoCapitulos, CapitulosUtil, ReporteFaltantes y MotorDescargaParalela.
 */
public class DescargaCli {
private static final Logger log = LoggerFactory.getLogger(DescargaCli.class);

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
            log.error("Excepción no controlada", e);
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
            log.error("Excepción no controlada", e);
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

        Map<Double, String> todosLosCapitulos = CatalogoCapitulos.obtener(urlPortada, System.out::println);

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
            log.error("Excepción no controlada", e);
        } finally {
            scraper.cerrar();
        }
    }

    private static void descargarRangoDeCapitulos(Scanner scanner, String rutaNovelaStr) {
        System.out.println("Introduce la URL de la PORTADA de la novela:");
        String urlPortada = scanner.nextLine();

        Map<Double, String> todosLosCapitulos = CatalogoCapitulos.obtener(urlPortada, System.out::println);

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
        Map<Double, String> todosLosCapitulos = CatalogoCapitulos.obtener(urlPortada, System.out::println);

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

    private static void ejecutarDescargaParalela(Map<Double, String> capitulosParaDescargar, String rutaDeGuardado,
            Scanner scanner) {
        if (capitulosParaDescargar.isEmpty()) {
            System.out.println("No hay capítulos para descargar.");
            return;
        }

        // Reanudar: saltar los capítulos cuyo .xhtml ya está en disco (tras un corte/bloqueo, solo
        // se bajan los que faltan; también reduce peticiones en reintentos).
        Map<Double, String> pendientes = new TreeMap<>();
        int yaDescargados = 0;
        for (Map.Entry<Double, String> e : capitulosParaDescargar.entrySet()) {
            if (EscritorArchivo.yaDescargado(rutaDeGuardado, e.getKey())) {
                yaDescargados++;
            } else {
                pendientes.put(e.getKey(), e.getValue());
            }
        }
        if (yaDescargados > 0) {
            System.out.println("Reanudando: " + yaDescargados + " capítulos ya estaban descargados; se omiten.");
        }
        if (pendientes.isEmpty()) {
            System.out.println("Todos los capítulos ya estaban descargados. Nada que hacer.");
            return;
        }
        capitulosParaDescargar = pendientes;

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

        // Tope por sitio (anti rate-limit): algunos sitios bloquean por peticiones/min (p. ej.
        // Wordfence en novelaenespanol.com). Respetamos su recomendación para no provocar bloqueos.
        SitioWebConfig cfgSitio = GestorSitios.obtenerConfig(capitulosParaDescargar.values().iterator().next());
        int maxRecomendado = cfgSitio.getMaxNavegadoresRecomendado();
        if (numeroHilos > maxRecomendado) {
            System.out.println("Nota: " + cfgSitio.getNombreSitio() + " limita el ritmo de peticiones; "
                    + "se usaran " + maxRecomendado + " navegadores (en vez de " + numeroHilos
                    + ") para evitar bloqueos.");
            numeroHilos = maxRecomendado;
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
            ReporteFaltantes.escribir(Paths.get(rutaDeGuardado), resultado.faltantes, resultado.vip);
        }

        imprimirResumenDescarga(resultado, numeroHilos);

        if (resultado.abortadoPorBloqueo) {
            System.out.println();
            System.out.println("  AVISO: la descarga se detuvo porque el sitio bloqueó temporalmente tu IP");
            System.out.println("  (demasiadas peticiones). Espera unos minutos y vuelve a ejecutar la MISMA");
            System.out.println("  descarga: se reanudará desde donde quedó (lo ya bajado no se repite).");
        }

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
}
