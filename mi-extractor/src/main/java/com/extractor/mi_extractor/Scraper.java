package com.extractor.mi_extractor;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.InputStream;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.openqa.selenium.PageLoadStrategy;

public class Scraper {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(Scraper.class);

    private WebDriver driver;
    private WebDriverWait wait;
    private JavascriptExecutor js;
    private SitioWebConfig configActual;
    private boolean permisoAdquirido = false; // permiso del cupo global de navegadores

    private final String selectorContenidoCapitulo = "div.skn-chp-chapter-content";

    public Scraper() {

        Properties props = new Properties();
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("application.properties")) {
            if (input != null) {
                props.load(input);
            }
        } catch (Exception e) {
            log.warn("Error cargando application.properties: " + e.getMessage());
        }
        String os = System.getProperty("os.name").toLowerCase();
        String driverPath = "";

        boolean esWindows = os.contains("win");
        if (esWindows) {
            driverPath = props.getProperty("webdriver.chrome.path.windows", "drivers/chromedriver.exe");
        } else if (os.contains("nix") || os.contains("nux") || os.contains("aix")) {
            driverPath = props.getProperty("webdriver.chrome.path.linux", "drivers/chromedriver-linux");
        }

        // Preferir el chromedriver LOCAL. Se prueban varias rutas porque el directorio de
        // trabajo cambia según cómo se arranque (repo raíz vs. carpeta del módulo).
        String relativo = esWindows ? "drivers/chromedriver.exe" : "drivers/chromedriver-linux";
        java.util.List<String> candidatos = new java.util.ArrayList<>();
        if (driverPath != null && !driverPath.isBlank()) {
            candidatos.add(driverPath);
        }
        candidatos.add(relativo);                    // CWD = mi-extractor/
        candidatos.add("mi-extractor/" + relativo);  // CWD = raíz del repo

        String driverLocal = null;
        for (String c : candidatos) {
            if (new java.io.File(c).exists()) {
                driverLocal = new java.io.File(c).getAbsolutePath();
                break;
            }
        }

        if (driverLocal != null) {
            // Ya hay un driver local: usarlo tal cual, sin descargar nada.
            System.setProperty("webdriver.chrome.driver", driverLocal);
            log.debug("Usando chromedriver local: " + driverLocal);
        } else {
            // No hay driver local: que Selenium Manager lo descargue UNA vez y lo cachee
            // (~/.cache/selenium) para reutilizarlo en los siguientes arranques.
            System.clearProperty("webdriver.chrome.driver");
            log.debug("No se encontró chromedriver local; Selenium Manager lo descargará una vez y lo reutilizará.");
        }
        ChromeOptions options = new ChromeOptions();

        options.setPageLoadStrategy(PageLoadStrategy.EAGER);
        Map<String, Object> prefs = new HashMap<>();
        prefs.put("profile.managed_default_content_settings.images", 2);
        options.setExperimentalOption("prefs", prefs);

        options.setExperimentalOption("excludeSwitches", java.util.Arrays.asList("enable-automation"));
        options.setExperimentalOption("useAutomationExtension", false);
        options.addArguments("--disable-blink-features=AutomationControlled");
        options.addArguments(
                "--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.7778.97 Safari/537.36");
        options.addArguments("--blink-settings=imagesEnabled=false");
        options.addArguments("--remote-allow-origins=*");
        options.addArguments("--headless=new");
        options.addArguments("--disable-gpu");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--disable-extensions");

        // Tope GLOBAL: pedir un permiso antes de lanzar Chrome. Si ya hay el máximo de
        // navegadores vivos, este hilo espera aquí hasta que se libere uno (protege la RAM).
        try {
            ConfigScraper.cupo().acquire();
            this.permisoAdquirido = true;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrumpido esperando un navegador disponible", ie);
        }

        try {
            this.driver = new ChromeDriver(options);
        } catch (RuntimeException e) {
            // Chrome no arrancó: liberar el permiso para no bloquear a otros hilos.
            ConfigScraper.cupo().release();
            this.permisoAdquirido = false;
            throw e;
        }
        // Tope de carga de página: si una página no carga en 30 s, falla rápido en vez de
        // colgar el hilo hasta 5 min (pageLoad por defecto = 300 s).
        this.driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(30));
        this.wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        this.js = (JavascriptExecutor) driver;

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (this.driver != null) {
                try {
                    log.debug("Limpiando procesos huérfanos de Chrome...");
                    this.driver.quit();
                } catch (Exception e) {
                    // Ignorar errores durante el apagado de emergencia
                }
            }
        }));
    }

    public SitioWebConfig getConfig() {
        return this.configActual;
    }

    public void navegarA(String url) {
        this.configActual = GestorSitios.obtenerConfig(url);
        if (this.configActual == null) {
            log.warn("ADVERTENCIA: No se encontró una configuración para esta URL.");
        }
        driver.get(url);
    }

    public String obtenerHtmlDePagina() {
        try {
            String selectorContenido = configActual.getSelectorContenido();
            wait.until(ExpectedConditions.presenceOfElementLocated(
                    By.cssSelector(selectorContenido)));
            return driver.getPageSource();

        } catch (Exception e) {
            // Mensaje conciso: el reintento del llamador decide si es un fallo real.
            // (Antes se volcaba el stack completo de Selenium en cada intento -> ruido alarmante.)
            return null;
        }
    }

    /** Selector del cartel de capítulo VIP/bloqueado en SkyNovels. */
    private static final By SELECTOR_VIP = By.cssSelector("div.skn-vip-locked-message");

    /** Resultado de pedir un capítulo: trae el HTML, o indica que es VIP (bloqueado). */
    public static class ResultadoCapitulo {
        public final String html; // null si no hubo contenido
        public final boolean vip;

        ResultadoCapitulo(String html, boolean vip) {
            this.html = html;
            this.vip = vip;
        }
    }

    /**
     * Espera a que aparezca el contenido del capítulo O el cartel VIP, lo que ocurra primero.
     * Así los capítulos VIP se detectan al instante y no gastan reintentos.
     */
    public ResultadoCapitulo obtenerContenidoOVip() {
        By contenido = By.cssSelector(configActual.getSelectorContenido());
        try {
            wait.until(d -> !d.findElements(contenido).isEmpty() || !d.findElements(SELECTOR_VIP).isEmpty());
        } catch (Exception e) {
            return new ResultadoCapitulo(null, false); // timeout -> fallo real (reintentar)
        }
        if (!driver.findElements(SELECTOR_VIP).isEmpty()) {
            return new ResultadoCapitulo(null, true); // VIP: no hay texto que bajar
        }
        return new ResultadoCapitulo(driver.getPageSource(), false);
    }

    /**
     * Extracción del índice (ADR-004): delega en la estrategia del sitio. La maña específica de
     * SkyNovels (turbo asíncrono) vive ahora en {@link SitioSkyNovels}; aquí solo se orquesta.
     */
    public String obtenerHtmlDeIndice() {
        if (configActual == null) {
            configActual = GestorSitios.obtenerConfig(driver.getCurrentUrl());
        }
        return configActual.extraerIndiceHtml(driver, js, wait);
    }

    private boolean esperarSelector(WebDriverWait waitLocal, String selector) {
        if (selector == null || selector.isBlank()) {
            return false;
        }
        try {
            waitLocal.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(selector)));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean activarPestanaCapitulos() {
        cerrarAvisoPublicidad();
        if (activarTabPorTextoCss("ul.nav-tabs a.nav-link", "contenido")) {
            return true;
        }
        String[] xpaths = new String[] {
                "//a[contains(translate(text(),'ÍÍÁÉÓÚ','IIAEOU'),'CAPITULO')]",
                "//a[contains(translate(text(),'ÍÍÁÉÓÚ','IIAEOU'),'CAPITULOS')]",
                "//a[contains(translate(text(),'ÍÍÁÉÓÚ','IIAEOU'),'INDICE')]",
                "//a[contains(translate(text(),'ÍÍÁÉÓÚ','IIAEOU'),'CONTENIDO')]",
                "//button[contains(translate(text(),'ÍÍÁÉÓÚ','IIAEOU'),'CAPITULO')]",
                "//button[contains(translate(text(),'ÍÍÁÉÓÚ','IIAEOU'),'INDICE')]"
        };
        for (String xpath : xpaths) {
            try {
                List<WebElement> elementos = driver.findElements(By.xpath(xpath));
                if (!elementos.isEmpty()) {
                    WebElement boton = elementos.get(0);
                    js.executeScript("arguments[0].click();", boton);
                    Thread.sleep(1500);
                    return true;
                }
            } catch (Exception e) {
            }
        }
        return false;
    }

    private boolean activarTabPorTextoCss(String selector, String texto) {
        try {
            List<WebElement> elementos = driver.findElements(By.cssSelector(selector));
            String textoNormalizado = texto.toLowerCase();
            for (WebElement elemento : elementos) {
                if (elemento.getText() != null && elemento.getText().toLowerCase().contains(textoNormalizado)) {
                    js.executeScript("arguments[0].click();", elemento);
                    Thread.sleep(1500);
                    return true;
                }
            }
        } catch (Exception e) {
        }
        return false;
    }

    private void cerrarAvisoPublicidad() {
        try {
            List<WebElement> botones = driver
                    .findElements(By.cssSelector(".adblock-toast__btn, .adblock-toast__close"));
            if (!botones.isEmpty()) {
                js.executeScript("arguments[0].click();", botones.get(0));
                Thread.sleep(500);
            }
        } catch (Exception e) {
        }
    }

    public boolean irAlSiguienteCapitulo() {
        try {
            String urlActual = driver.getCurrentUrl();

            // 1. Selector Universal: Busca enlaces o botones que digan "siguiente", "next" o tengan esas clases
            String xpathSiguiente = "//a[contains(translate(., 'ABCDEFGHIJKLMNOPQRSTUVWXYZÁÉÍÓÚ', 'abcdefghijklmnopqrstuvwxyzáéíóú'), 'siguiente')] " +
                                    "| //button[contains(translate(., 'ABCDEFGHIJKLMNOPQRSTUVWXYZÁÉÍÓÚ', 'abcdefghijklmnopqrstuvwxyzáéíóú'), 'siguiente')] " +
                                    "| //a[contains(translate(., 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), 'next')] " +
                                    "| //*[contains(@class, 'btn-next') or contains(@class, 'next-chap')]";

            java.util.List<WebElement> botones = driver.findElements(By.xpath(xpathSiguiente));
            WebElement botonClick = null;

            for (WebElement btn : botones) {
                // Filtramos botones ocultos o que estén desactivados (común en el último capítulo de la novela)
                if (btn.isDisplayed() && btn.getAttribute("disabled") == null && !btn.getAttribute("class").contains("disabled")) {
                    botonClick = btn;
                }
            }

            if (botonClick != null) {
                log.info("   -> Botón 'Siguiente' detectado. Avanzando...");

                String href = botonClick.getAttribute("href");

                // 2. Navegación Inteligente
                if (href != null && !href.isEmpty() && !href.trim().equals("#") && !href.contains("javascript")) {

                    driver.get(href);
                } else {
                    js.executeScript("arguments[0].click();", botonClick);
                }

                // 3. Esperamos el cambio (Angular no recarga la página, solo cambia la URL internamente)
                try {
                    WebDriverWait waitCorto = new WebDriverWait(driver, Duration.ofSeconds(5));
                    waitCorto.until(ExpectedConditions.not(ExpectedConditions.urlToBe(urlActual)));
                } catch (Exception e) {
                    // Si la URL no cambia a tiempo, asumimos que Angular está cargando el texto
                }

                // 4. Verificamos que el nuevo contenido ya esté visible en pantalla
                String selectorContenido = configActual.getSelectorContenido();
                wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(selectorContenido)));

                Thread.sleep(1200); // Pausa de cortesía para que Angular pinte el DOM por completo
                return true;

            } else {
                log.info("   -> No se encontró botón 'Siguiente' válido. ¡Se alcanzó el final de la novela!");
                return false;
            }

        } catch (Exception e) {
            log.info("   -> Error al intentar avanzar: " + e.getMessage());
            return false;
        }
    }

    public void cerrar() {
        if (driver != null) {
            log.debug("Cerrando el navegador.");
            try {
                driver.quit();
            } catch (Exception e) {
                // El navegador ya pudo morir: ignorar para no dejar la limpieza a medias.
                log.warn("Aviso al cerrar el navegador: " + e.getMessage());
            } finally {
                driver = null; // evita un segundo quit sobre un driver ya cerrado
            }
        }
        // Devolver el permiso al cupo global (una sola vez) para que otro hilo pueda abrir Chrome.
        if (permisoAdquirido) {
            ConfigScraper.cupo().release();
            permisoAdquirido = false;
        }
    }
}
