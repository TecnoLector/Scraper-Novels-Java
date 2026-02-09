package com.extractor.mi_extractor;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.openqa.selenium.PageLoadStrategy;

public class Scraper {
    private WebDriver driver;
    private WebDriverWait wait;
    private JavascriptExecutor js;
    private SitioWebConfig configActual;

    private final String selectorContenidoCapitulo = "div.skn-chp-chapter-content";
    private final String xPathPestanaContenido = "//a[contains(@class, 'nav-link') and contains(text(), 'Contenido')]";
    private final String selectorEncabezadoVolumen = "mat-expansion-panel-header";
    private final String selectorElementoDeLaLista = "div.skn-nvl-chp-element";
    private final String xPathBotonSiguiente = "//button[.//span[contains(text(), 'Siguiente')]]";

    public Scraper() {
        ChromeOptions options = new ChromeOptions();

        options.setPageLoadStrategy(PageLoadStrategy.EAGER);
        // options.addArguments("--headless=new");
        Map<String, Object> prefs = new HashMap<>();
        prefs.put("profile.managed_default_content_settings.images", 2);
        options.setExperimentalOption("prefs", prefs);
        options.setExperimentalOption("excludeSwitches", java.util.Arrays.asList("enable-automation"));
        options.setExperimentalOption("useAutomationExtension", false);
        options.addArguments("--disable-blink-features=AutomationControlled");
        options.addArguments("--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        options.addArguments("--blink-settings=imagesEnabled=false");

        // options.addArguments("--disable-javascript");
        this.driver = new ChromeDriver(options);
        this.wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        this.js = (JavascriptExecutor) driver;
    }
    public SitioWebConfig getConfig() {
    return this.configActual;
}

    public void navegarA(String url) {
    this.configActual = GestorSitios.obtenerConfig(url);
    if (this.configActual == null) {
        System.err.println("ADVERTENCIA: No se encontró una configuración para esta URL.");
    }
    driver.get(url);
}

     public String obtenerHtmlDePagina() {
        try {
            // 1. Averiguamos qué buscar según la configuración
            String selectorContenido = configActual.getSelectorContenido(); 
            
            // 2. Esperamos a que el elemento EXISTA (para saber que la página cargó)
            wait.until(ExpectedConditions.presenceOfElementLocated(
                By.cssSelector(selectorContenido)
            ));
            
            // 3. CAMBIO IMPORTANTE:
            // Antes devolvíamos solo el elemento (lo que dejaba fuera el título).
            // Ahora devolvemos TODA la página para que el Extractor tenga todo el contexto.
            return driver.getPageSource(); 

        } catch (Exception e) {
            System.err.println("Error esperando/extrayendo HTML: " + e.getMessage());
            return null;
        }
    }

    // En Scraper.java
    public String obtenerHtmlDeIndice() {
        try {
            // --- PASO 1: Abrir Pestaña Contenido ---
            try {
                String xPathContenido = "//a[contains(text(), 'Contenido')]";
                WebDriverWait waitBreve = new WebDriverWait(driver, Duration.ofSeconds(3));
                WebElement pestana = waitBreve.until(ExpectedConditions.elementToBeClickable(By.xpath(xPathContenido)));
                
                if (pestana != null) {
                    System.out.println("Pestaña 'Contenido' detectada. Haciendo clic...");
                    js.executeScript("arguments[0].click();", pestana);
                    Thread.sleep(2000); 
                }
            } catch (Exception e) {
                System.out.println("Nota: No se necesitó cambiar de pestaña o ya estaba activa.");
            }

            // --- PASO 2: Esperar carga inicial ---
            System.out.println("Esperando estructura de capítulos...");
            String selectorLista = configActual.getSelectorContenedorIndice(); 
            
            // Esperamos a que aparezca ALGO (capítulos sueltos o volúmenes cerrados)
            wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(selectorLista)));

            // --- PASO 3: ABRIR VOLÚMENES (NUEVO) ---
            // Buscamos si hay paneles de expansión (volúmenes) y los abrimos
            try {
                java.util.List<WebElement> paneles = driver.findElements(By.cssSelector("mat-expansion-panel-header"));
                if (!paneles.isEmpty()) {
                    System.out.println("Se detectaron " + paneles.size() + " volúmenes. Abriendo todos...");
                    for (WebElement panel : paneles) {
                        // Verificamos si está cerrado antes de clicar (opcional, pero el script es seguro)
                        js.executeScript("arguments[0].click();", panel);
                        Thread.sleep(500); // Pequeña pausa entre clics para no saturar
                    }
                    System.out.println("Volúmenes expandidos.");
                    Thread.sleep(2000); // Esperar a que el contenido de los paneles se renderice
                }
            } catch (Exception e) {
                System.out.println("Error intentando abrir volúmenes (puede que no haya): " + e.getMessage());
            }

            // --- PASO 4: Scroll final ---
            js.executeScript("window.scrollTo(0, document.body.scrollHeight);");
            Thread.sleep(2000);

            return driver.getPageSource();

        } catch (Exception e) {
            System.err.println("Advertencia obteniendo índice: " + e.getMessage());
            // Guardamos un debug visual si falla
            return driver.getPageSource(); 
        }
    }
    
    public List<String> obtenerEnlacesCapitulosDesdeDom() {
        Set<String> enlaces = new HashSet<>();
        try {
            activarPestanaCapitulos();
            cerrarAvisoPublicidad();
            for (int i = 0; i < 6; i++) {
                List<WebElement> elementos = driver.findElements(
                        By.cssSelector("a[href*='/capitulo/'], a[href*='/novelas/'][href*='capitulo']"));
                for (WebElement elemento : elementos) {
                    String href = elemento.getAttribute("href");
                    if (href != null && !href.isBlank()) {
                        enlaces.add(href);
                    }
                }
                js.executeScript("window.scrollTo(0, document.body.scrollHeight);");
                Thread.sleep(1200);
            }
        } catch (Exception e) {
            // continuar
        }
        return new java.util.ArrayList<>(enlaces);
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
                // continuar
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
            // continuar
        }
        return false;
    }

    private void cerrarAvisoPublicidad() {
        try {
            List<WebElement> botones = driver.findElements(By.cssSelector(".adblock-toast__btn, .adblock-toast__close"));
            if (!botones.isEmpty()) {
                js.executeScript("arguments[0].click();", botones.get(0));
                Thread.sleep(500);
            }
        } catch (Exception e) {
            // continuar
        }
    }


    public boolean irAlSiguienteCapitulo() {

        try {
            wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector(selectorContenidoCapitulo)));
            return true;
        } catch (Exception e) {
            return false; // Si falla la espera, asumimos que no cargó
        }
        /*
         * try {
         * WebElement botonSiguiente =
         * driver.findElement(By.xpath(xPathBotonSiguiente));
         * js.executeScript("arguments[0].click();", botonSiguiente);
         * System.out.println("Haciendo clic en el botón 'Siguiente'...");
         * //
         * System.out.println("Esperando 0.5 segundos a que cargue el nuevo capítulo..."
         * );
         * //Thread.sleep(2000);
         * // System.out.
         * println("Esperando a que cargue el contenido del SIGUIENTE capítulo...");
         * wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(
         * selectorContenidoCapitulo)));
         * //System.out.println("¡Siguiente capítulo cargado!");
         * return true;
         * } catch (Exception e) {
         * System.out.println("No se encontró el botón 'Siguiente'.");
         * return false;
         * }
         */
    }

    public void cerrar() {
        if (driver != null) {
            System.out.println("Cerrando el navegador.");
            driver.quit();
        }
    }
}