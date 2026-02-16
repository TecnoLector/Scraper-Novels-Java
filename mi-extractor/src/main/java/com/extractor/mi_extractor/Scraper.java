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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.openqa.selenium.PageLoadStrategy;

public class Scraper {
    private WebDriver driver;
    private WebDriverWait wait;
    private JavascriptExecutor js;
    private SitioWebConfig configActual;

    private final String selectorContenidoCapitulo = "div.skn-chp-chapter-content";

    public Scraper() {
        System.setProperty("webdriver.chrome.driver", "C:\\Users\\tecno\\Documents\\Novelas Sigil\\ProgramaExtracion\\mi-extractor\\chromedriver.exe");
        ChromeOptions options = new ChromeOptions();

        options.setPageLoadStrategy(PageLoadStrategy.EAGER);
        Map<String, Object> prefs = new HashMap<>();
        prefs.put("profile.managed_default_content_settings.images", 2);
        options.setExperimentalOption("prefs", prefs);
        
        options.setExperimentalOption("excludeSwitches", java.util.Arrays.asList("enable-automation"));
        options.setExperimentalOption("useAutomationExtension", false);
        options.addArguments("--disable-blink-features=AutomationControlled");
        options.addArguments("--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        options.addArguments("--blink-settings=imagesEnabled=false");
        options.addArguments("--remote-allow-origins=*");

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
            String selectorContenido = configActual.getSelectorContenido(); 
            wait.until(ExpectedConditions.presenceOfElementLocated(
                By.cssSelector(selectorContenido)
            ));
            return driver.getPageSource(); 

        } catch (Exception e) {
            System.err.println("Error esperando/extrayendo HTML: " + e.getMessage());
            return null;
        }
    }

    // En Scraper.java
    public String obtenerHtmlDeIndice() {
        try {
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
            System.out.println("Esperando estructura de capítulos...");
            String selectorLista = configActual.getSelectorContenedorIndice(); 
            wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(selectorLista)));
            try {
                java.util.List<WebElement> paneles = driver.findElements(By.cssSelector("mat-expansion-panel-header"));
                if (!paneles.isEmpty()) {
                    System.out.println("Se detectaron " + paneles.size() + " volúmenes. Abriendo todos...");
                    for (WebElement panel : paneles) {
                        js.executeScript("arguments[0].click();", panel);
                        Thread.sleep(500);
                    }
                    System.out.println("Volúmenes expandidos.");
                    Thread.sleep(2000);
                }
            } catch (Exception e) {
                System.out.println("Error intentando abrir volúmenes (puede que no haya): " + e.getMessage());
            }
            js.executeScript("window.scrollTo(0, document.body.scrollHeight);");
            Thread.sleep(2000);

            return driver.getPageSource();

        } catch (Exception e) {
            System.err.println("Advertencia obteniendo índice: " + e.getMessage());
            return driver.getPageSource(); 
        }
    }
    
    public List<String> obtenerEnlacesCapitulosDesdeDom() {
        Set<String> enlaces = new HashSet<>();
        
        try {
            // 1. MANIOBRA ESPECIAL PARA SKYNOVELS
            if (driver.getCurrentUrl().contains("skynovels.net")) {
                System.out.println("⚡ Detectado SkyNovels. Abriendo menús...");
                Thread.sleep(2000); // Esperar a que cargue la web

                // A. Intentar clic en cualquier cosa que diga "Contenido"
                try {
                    // Este XPath busca texto "Contenido" en cualquier etiqueta (div, span, a, p)
                    List<WebElement> pestanas = driver.findElements(By.xpath("//*[contains(text(),'Contenido')]"));
                    for (WebElement p : pestanas) {
                        // Solo hacemos clic si es visible
                        if (p.isDisplayed()) {
                            js.executeScript("arguments[0].click();", p);
                            Thread.sleep(500); // Pausa tras el clic
                        }
                    }
                } catch (Exception e) {}

                // B. Abrir los acordeones (Paneles)
                try {
                    List<WebElement> paneles = driver.findElements(By.cssSelector("mat-expansion-panel-header"));
                    if (!paneles.isEmpty()) {
                        System.out.println("   -> Desplegando " + paneles.size() + " volúmenes...");
                        for (WebElement panel : paneles) {
                            js.executeScript("arguments[0].click();", panel);
                            Thread.sleep(100);
                        }
                    }
                } catch (Exception e) {}

                System.out.println("   -> Esperando 3 segundos a que aparezcan los enlaces...");
                Thread.sleep(3000); 
            }

            // 2. EXTRACCIÓN DE ENLACES (Ahora que todo está abierto)
            System.out.println("🔍 Buscando capítulos en la página...");
            
            // Hacemos 3 barridos con scroll para asegurar que capturamos todo
            for (int i = 0; i < 3; i++) {
                List<WebElement> elementos = driver.findElements(
                    By.cssSelector("a[href*='/capitulo/'], a[href*='/novelas/'][href*='capitulo']"));
                
                for (WebElement el : elementos) {
                    String href = el.getAttribute("href");
                    if (href != null && !href.isEmpty()) {
                        enlaces.add(href);
                    }
                }
                
                // Scroll hacia abajo para cargar más (Lazy Loading)
                js.executeScript("window.scrollTo(0, document.body.scrollHeight);");
                Thread.sleep(1000);
            }
            
            System.out.println("✅ Total encontrados: " + enlaces.size());

        } catch (Exception e) {
            System.out.println("Error extrayendo enlaces: " + e.getMessage());
        }
        
        return new ArrayList<>(enlaces);
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
            List<WebElement> botones = driver.findElements(By.cssSelector(".adblock-toast__btn, .adblock-toast__close"));
            if (!botones.isEmpty()) {
                js.executeScript("arguments[0].click();", botones.get(0));
                Thread.sleep(500);
            }
        } catch (Exception e) {
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