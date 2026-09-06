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

import java.io.InputStream;
import java.time.Duration;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
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

        Properties props = new Properties();
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("application.properties")) {
            if (input != null) {
                props.load(input);
            }
        } catch (Exception e) {
            System.err.println("Error cargando application.properties: " + e.getMessage());
        }
        String os = System.getProperty("os.name").toLowerCase();
        String driverPath = "";

        if (os.contains("win")) {
            driverPath = props.getProperty("webdriver.chrome.path.windows", "drivers/chromedriver.exe");
        } else if (os.contains("nix") || os.contains("nux") || os.contains("aix")) {
            driverPath = props.getProperty("webdriver.chrome.path.linux", "drivers/chromedriver-linux");
        }
        System.setProperty("webdriver.chrome.driver", driverPath);
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
        options.addArguments("--memory-pressure-thresholds-mb=50");

        this.driver = new ChromeDriver(options);
        this.wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        this.js = (JavascriptExecutor) driver;

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (this.driver != null) {
                try {
                    System.out.println("Limpiando procesos huérfanos de Chromee...");
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
            System.err.println("ADVERTENCIA: No se encontró una configuración para esta URL.");
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
            System.err.println("Error esperando/extrayendo HTML: " + e.getMessage());
            return null;
        }
    }

    public String obtenerHtmlDeIndice() {
        try {
            // 1. Intentar hacer clic en la pestaña "Contenido"
            try {
                String xPathContenido = "//button[contains(@class, 'nvl-tab-btn')]//span[contains(text(), 'Contenido')] | //button[contains(., 'Contenido')] | //span[contains(text(), 'Contenido')]";
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
            
            // --- LÓGICA ESPECIAL SKYNOVELS (TURBO JAVASCRIPT) ---
            if (driver.getCurrentUrl().contains("skynovels.net")) {
                System.out.println("Modo SkyNovels: Extracción asíncrona iniciada...");
                java.util.Set<String> enlacesGuardados = new java.util.LinkedHashSet<>();
                
                // IMPORTANTE: Le damos permiso a Selenium de esperar la respuesta asíncrona
                driver.manage().timeouts().scriptTimeout(Duration.ofSeconds(60));
                
                // Preparamos el Script Turbo (Se ejecuta adentro de Chrome sin lag de Java)
                String jsTurbo = 
                    "var callback = arguments[arguments.length - 1];" +
                    "var enlaces = [];" +
                    "var urls = new Set();" +
                    "var scrollArea = document.querySelector('.mat-expansion-panel.mat-expanded .mat-expansion-panel-content, .cdk-virtual-scroll-viewport, .mat-expansion-panel-body') || document.scrollingElement || window;" +
                    "var intentos = 0;" +
                    "var lastScroll = -1;" +
                    "var timer = setInterval(function() {" +
                    "    var elementos = document.querySelectorAll(\"a[href*='/capitulo/'], a[href*='/novelas/'][href*='capitulo'], div.skn-nvl-chp-element a\");" +
                    "    elementos.forEach(function(el) {" +
                    "        if (el.href && el.href.includes('capitulo') && !urls.has(el.href)) {" +
                    "            urls.add(el.href);" +
                    "            enlaces.push('<a href=\"' + el.href + '\">' + (el.innerText.trim() || 'Capitulo') + '</a>');" +
                    "        }" +
                    "    });" +
                    "    var currentScroll = (scrollArea === window || scrollArea === document.scrollingElement) ? window.scrollY : scrollArea.scrollTop;" +
                    "    if (scrollArea !== window && scrollArea !== document.scrollingElement) { scrollArea.scrollTop += 6000; }" +
                    "    window.scrollBy(0, 6000);" +
                    "    if (currentScroll === lastScroll) {" +
                    "        intentos++;" +
                    "        if (intentos >= 3) {" +
                    "            clearInterval(timer);" +
                    "            callback(enlaces);" + // Envía todo de vuelta a Java
                    "        }" +
                    "    } else {" +
                    "        intentos = 0;" +
                    "        lastScroll = currentScroll;" +
                    "    }" +
                    "}, 80);"; // <-- Se evalúa cada 80 milisegundos a máxima velocidad
                
                java.util.List<WebElement> paneles = driver.findElements(By.cssSelector("mat-expansion-panel-header"));
                
                if (!paneles.isEmpty()) {
                    System.out.println("\n Se detectaron " + paneles.size() + " volúmenes. Analizando a velocidad extrema...");
                    for (int i = 0; i < paneles.size(); i++) {
                        paneles = driver.findElements(By.cssSelector("mat-expansion-panel-header"));
                        if (i >= paneles.size()) break;
                        WebElement panel = paneles.get(i);
                        
                        js.executeScript("arguments[0].scrollIntoView({behavior: 'instant', block: 'center'});", panel);
                        Thread.sleep(200);
                        
                        String expandido = panel.getAttribute("aria-expanded");
                        if (!"true".equals(expandido)) {
                            js.executeScript("arguments[0].click();", panel);
                            Thread.sleep(600); // Dar 0.6s para que Angular abra el panel
                        }
                        
                        System.out.print(" -> Ecaneando Volumen " + (i + 1) + "...");
                        
                        // Aquí llamamos al Turbo Script y Java espera pacientemente (en milisegundos)
                        @SuppressWarnings("unchecked")
                        java.util.List<String> enlacesExtraidosJS = (java.util.List<String>) js.executeAsyncScript(jsTurbo);
                        
                        if (enlacesExtraidosJS != null) {
                            enlacesGuardados.addAll(enlacesExtraidosJS);
                            System.out.println("\n  -> En memoria: " + enlacesGuardados.size());
                        }
                    }
                } else {
                    System.out.println("No se detectaron volúmenes agrupados. Extrayendo lista general con Turbo...");
                    @SuppressWarnings("unchecked")
                    java.util.List<String> enlacesExtraidosJS = (java.util.List<String>) js.executeAsyncScript(jsTurbo);
                    if (enlacesExtraidosJS != null) enlacesGuardados.addAll(enlacesExtraidosJS);
                }
                
                System.out.println("\n ¡Escaneo completo! Total de capítulos capturados en memoria: " + enlacesGuardados.size());
                
                // Creamos el HTML "limpio" y perfecto para que Jsoup no se confunda
                StringBuilder domReconstruido = new StringBuilder();
                domReconstruido.append("<!DOCTYPE html><html><head><title>SkyNovels Limpio</title></head><body>\n");
                domReconstruido.append("<mat-expansion-panel>\n"); 
                
                for (String enlaceHtml : enlacesGuardados) {
                    domReconstruido.append("<div class=\"skn-nvl-chp-element\">"); 
                    domReconstruido.append(enlaceHtml);
                    domReconstruido.append("</div>\n");
                }
                
                domReconstruido.append("</mat-expansion-panel>\n</body></html>");
                return domReconstruido.toString();

            } else {
                // --- LÓGICA RÁPIDA PARA DEMÁS PÁGINAS (NOVA, TNL, BLOGGER) ---
                try {
                    java.util.List<WebElement> paneles = driver.findElements(By.cssSelector("mat-expansion-panel-header"));
                    if (!paneles.isEmpty()) {
                        System.out.println("Se detectaron " + paneles.size() + " volúmenes. Abriendo todos...");
                        for (WebElement panel : paneles) {
                            js.executeScript("arguments[0].click();", panel);
                            Thread.sleep(500); 
                        }
                    }
                } catch (Exception e) {}

                System.out.println("Cargando página completa...");
                js.executeScript("window.scrollTo(0, document.body.scrollHeight);");
                Thread.sleep(1500);
                System.out.println("Scroll finalizado. Extrayendo código fuente...");
                return driver.getPageSource();
            }

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

                // A. Intentar clic en la pestaña "Contenido" (Actualizado para el nuevo Angular)
                try {
                    String xPathContenido = "//button[contains(@class, 'nvl-tab-btn')]//span[contains(text(), 'Contenido')] | //button[contains(., 'Contenido')]";
                    List<WebElement> pestanas = driver.findElements(By.xpath(xPathContenido));
                    for (WebElement p : pestanas) {
                        if (p.isDisplayed()) {
                            js.executeScript("arguments[0].click();", p);
                            Thread.sleep(1000); // Pausa tras el clic
                            break; // Si ya hizo clic, salimos del for
                        }
                    }
                } catch (Exception e) {
                    System.out.println("   -> No se pudo hacer clic en Contenido: " + e.getMessage());
                }

                // B. Abrir los acordeones (Paneles de Volúmenes)
                try {
                    List<WebElement> paneles = driver.findElements(By.cssSelector("mat-expansion-panel-header"));
                    if (!paneles.isEmpty()) {
                        System.out.println("   -> Desplegando " + paneles.size() + " volúmenes...");
                        for (WebElement panel : paneles) {
                            js.executeScript("arguments[0].click();", panel);
                            Thread.sleep(200); // Un poco más de tiempo para que Angular anime el panel
                        }
                    }
                } catch (Exception e) {
                }

                System.out.println("   -> Esperando a que aparezcan los enlaces...");
                Thread.sleep(2000);
            }

            // 2. EXTRACCIÓN DE ENLACES (Scroll Incremental para vencer a Angular)
            System.out.println("🔍 Buscando capítulos en la página con Scroll Incremental...");

            long lastScrollPosition = 0;
            int maxIntentosSinNuevosEnlaces = 5;
            int intentos = 0;

            // Bucle que baja como si fuera un humano leyendo
            while (intentos < maxIntentosSinNuevosEnlaces) {
                int cantidadAntes = enlaces.size();

                // Capturamos lo que está visible AHORA mismo
                List<WebElement> elementos = driver.findElements(
                        By.cssSelector("a[href*='/capitulo/'], a[href*='/novelas/'][href*='capitulo']"));

                for (WebElement el : elementos) {
                    String href = el.getAttribute("href");
                    if (href != null && !href.isEmpty()) {
                        enlaces.add(href);
                    }
                }

                // Hacemos un scroll suave hacia abajo (aprox media pantalla)
                js.executeScript("window.scrollBy(0, 800);");
                Thread.sleep(800); // Damos tiempo a que Angular cree los nuevos enlaces

                // Comprobamos dónde estamos y si encontramos algo nuevo
                long currentScrollPosition = (long) js.executeScript("return window.scrollY;");

                if (enlaces.size() == cantidadAntes) {
                    intentos++; // No encontramos enlaces nuevos, sumamos un "strike"
                } else {
                    intentos = 0; // Encontramos enlaces nuevos, reseteamos strikes
                }

                // Si la barra de scroll ya no bajó más, llegamos al final de la página
                if (currentScrollPosition == lastScrollPosition) {
                    break;
                }
                lastScrollPosition = currentScrollPosition;
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
                System.out.println("   -> Botón 'Siguiente' detectado. Avanzando...");
                
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
                System.out.println("   -> No se encontró botón 'Siguiente' válido. ¡Se alcanzó el final de la novela!");
                return false;
            }

        } catch (Exception e) {
            System.out.println("   -> Error al intentar avanzar: " + e.getMessage());
            return false;
        }
    }

    public void cerrar() {
        if (driver != null) {
            System.out.println("Cerrando el navegador.");
            driver.quit();
        }
    }
}