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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.openqa.selenium.PageLoadStrategy;

public class Scraper {
    private WebDriver driver;
    private WebDriverWait wait;
    private JavascriptExecutor js;
    private SitioWebConfig configActual;

    private final String selectorContenidoCapitulo = "div.skn-chp-chapter-content";
    private final String xPathPestanaContenido = "//a[@class='nav-link' and contains(text(), 'Contenido')]";
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
        this.wait = new WebDriverWait(driver, Duration.ofSeconds(1));
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
            WebDriverWait waitLargo = new WebDriverWait(driver, java.time.Duration.ofSeconds(30));
            waitLargo.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(selectorContenido)));
            return driver.getPageSource(); 

        } catch (Exception e) {
            System.err.println("Error esperando/extrayendo HTML: " + e.getMessage());
            return null;
        }
    }

    public String obtenerHtmlDeIndice() {
        try {
            System.out.println("Esperando a que cargue la lista de capítulos...");
            String selectorLista = configActual.getSelectorContenedorIndice(); 
            wait.until(ExpectedConditions.presenceOfElementLocated(
                By.cssSelector(selectorLista)
            ));
            js.executeScript("window.scrollTo(0, document.body.scrollHeight);");
            Thread.sleep(2000);
            return driver.getPageSource();

        } catch (Exception e) {
            System.err.println("Error obteniendo índice: " + e.getMessage());
            return null;
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