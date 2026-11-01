package com.extractor.mi_extractor;

import java.io.InputStream;
import java.util.Properties;
import java.util.concurrent.Semaphore;

/**
 * Configuración y control de concurrencia del scraping.
 *
 * <p>Mantiene un {@link Semaphore} GLOBAL con tantos permisos como navegadores
 * Chrome se permiten a la vez ({@code app.scraper.max-navegadores}, por defecto 10).
 * Todo {@code Scraper} pide un permiso antes de lanzar Chrome y lo suelta al cerrar,
 * de modo que —pasen las peticiones o hilos que pasen— nunca hay más de N navegadores
 * vivos simultáneamente. El resto espera en cola, protegiendo la RAM.</p>
 */
public final class ConfigScraper {

    private static final int DEFECTO_MAX = 10;

    private static volatile int maxNavegadores = -1;
    private static volatile Semaphore cupo;

    private ConfigScraper() {
    }

    /** Número máximo de navegadores simultáneos (leído de application.properties, mínimo 1). */
    public static int maxNavegadores() {
        if (maxNavegadores < 0) {
            inicializar();
        }
        return maxNavegadores;
    }

    /** Semáforo global que limita cuántos Chrome pueden estar vivos a la vez. */
    public static Semaphore cupo() {
        if (cupo == null) {
            inicializar();
        }
        return cupo;
    }

    private static synchronized void inicializar() {
        if (cupo != null) {
            return;
        }
        int max = DEFECTO_MAX;
        try (InputStream in = ConfigScraper.class.getClassLoader().getResourceAsStream("application.properties")) {
            if (in != null) {
                Properties p = new Properties();
                p.load(in);
                String s = p.getProperty("app.scraper.max-navegadores");
                if (s != null && !s.isBlank()) {
                    int n = Integer.parseInt(s.trim());
                    if (n >= 1) {
                        max = n;
                    }
                }
            }
        } catch (Exception ignored) {
            // Si no se puede leer, se queda el valor por defecto.
        }
        maxNavegadores = max;
        cupo = new Semaphore(max, true); // justo (FIFO) para no dejar hilos sin turno
    }
}
