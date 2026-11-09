package com.extractor.mi_extractor.scraping.model;

import java.time.Duration;
import java.util.List;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class SitioWebConfig {

    protected static final Logger log = LoggerFactory.getLogger(SitioWebConfig.class);

    public abstract String getNombreSitio();
    public abstract boolean esSoportado(String url);
    public abstract String getSelectorContenido();
    public abstract String getSelectorTitulo();
    public abstract String getSelectorContenedorIndice();
    public abstract String getSelectorElementoLista();
    public abstract String getSelectorEnlaceCapitulo();
    public abstract String getSelectorNumeroCapitulo();
    public String getSelectorSiguientePaginaIndice() {
        return null;
    }

    /**
     * Máximo de navegadores en paralelo recomendado para este sitio (anti rate-limit). Por defecto
     * sin tope; los sitios con límites estrictos de peticiones (p. ej. Wordfence) lo bajan para no
     * dispararlos. CLI y motor lo respetan.
     */
    public int getMaxNavegadoresRecomendado() {
        return Integer.MAX_VALUE;
    }

    /**
     * Pausa mínima (ms) entre capítulos dentro de cada hilo. El motor duerme un valor aleatorio
     * entre {@link #getPausaMinMs()} y {@link #getPausaMaxMs()} (patrón más natural que un fijo).
     * Por defecto 0 (sin pausa); los sitios sensibles al rate-limit la suben.
     */
    public long getPausaMinMs() {
        return 0;
    }

    /** Pausa máxima (ms) entre capítulos. Por defecto 0 (sin pausa). Ver {@link #getPausaMinMs()}. */
    public long getPausaMaxMs() {
        return 0;
    }

    /**
     * {@code true} si el HTML recibido es una página de bloqueo del sitio (p. ej. el 503 de
     * Wordfence por exceso de peticiones). Permite al motor abortar en vez de seguir martilleando.
     * Por defecto {@code false}; los sitios con anti-bot conocido lo detectan.
     */
    public boolean esPaginaDeBloqueo(String html) {
        return false;
    }

    /**
     * Preámbulo compartido del índice: hace clic en la pestaña "Contenido" si existe y espera a
     * que aparezca el contenedor del índice. La espera del contenedor puede lanzar (timeout); el
     * llamador lo captura, igual que antes.
     */
    protected void prepararIndice(WebDriver driver, JavascriptExecutor js, WebDriverWait wait) {
        try {
            String xPathContenido = "//button[contains(@class, 'nvl-tab-btn')]//span[contains(text(), 'Contenido')] | //button[contains(., 'Contenido')] | //span[contains(text(), 'Contenido')]";
            WebDriverWait waitBreve = new WebDriverWait(driver, Duration.ofSeconds(3));
            WebElement pestana = waitBreve.until(ExpectedConditions.elementToBeClickable(By.xpath(xPathContenido)));
            if (pestana != null) {
                log.debug("Pestaña 'Contenido' detectada. Haciendo clic...");
                js.executeScript("arguments[0].click();", pestana);
                Thread.sleep(2000);
            }
        } catch (Exception e) {
            log.debug("Nota: No se necesitó cambiar de pestaña o ya estaba activa.");
        }
        log.debug("Esperando estructura de capítulos...");
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(getSelectorContenedorIndice())));
    }

    /**
     * Estrategia de extracción del índice (ADR-004). Por defecto: abre los paneles de volúmenes
     * (si los hay), hace scroll hasta el final y devuelve el HTML de la página para que lo parsee
     * {@code IndiceExtractor}. Los sitios con mañas propias (p. ej. SkyNovels) la sobrescriben.
     */
    public String extraerIndiceHtml(WebDriver driver, JavascriptExecutor js, WebDriverWait wait) {
        try {
            prepararIndice(driver, js, wait);

            try {
                List<WebElement> paneles = driver.findElements(By.cssSelector("mat-expansion-panel-header"));
                if (!paneles.isEmpty()) {
                    log.debug("Se detectaron {} volúmenes. Abriendo todos...", paneles.size());
                    for (WebElement panel : paneles) {
                        js.executeScript("arguments[0].click();", panel);
                        Thread.sleep(500);
                    }
                }
            } catch (Exception e) {
                // Sin paneles agrupados (o no se pudieron abrir): seguimos con el scroll general.
            }

            log.debug("Cargando página completa...");
            js.executeScript("window.scrollTo(0, document.body.scrollHeight);");
            Thread.sleep(1500);
            log.debug("Scroll finalizado. Extrayendo código fuente...");
            return driver.getPageSource();

        } catch (Exception e) {
            // Los errores de Selenium traen un mensaje enorme (Build info/Capabilities/Session…);
            // en consola dejamos solo la causa y mandamos el detalle completo al log de fichero.
            log.warn("Advertencia obteniendo índice: {}", primeraLinea(e));
            log.debug("Detalle del fallo obteniendo índice", e);
            // Último recurso: intentar el HTML, pero sin que un navegador muerto vuelva a petar.
            try {
                return driver.getPageSource();
            } catch (Exception e2) {
                log.warn("No se pudo obtener el HTML tras el fallo: {}", primeraLinea(e2));
                return "<!DOCTYPE html><html><body></body></html>";
            }
        }
    }

    /** Primera línea del mensaje de una excepción (evita volcar el diagnóstico largo de Selenium). */
    private static String primeraLinea(Throwable t) {
        String msg = (t.getMessage() != null) ? t.getMessage() : t.getClass().getSimpleName();
        int nl = msg.indexOf('\n');
        return (nl >= 0) ? msg.substring(0, nl).trim() : msg.trim();
    }
}
