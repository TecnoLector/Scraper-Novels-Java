package com.extractor.mi_extractor;

import java.time.Duration;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SitioSkyNovels extends SitioWebConfig {

    @Override
    public String getNombreSitio() {
        return "SkyNovels (Oficial)";
    }

    @Override
    public boolean esSoportado(String url) {
        // IMPORTANTE: Debe llamarse 'soportaUrl' para coincidir con GestorSitios
        return url != null && url.contains("skynovels.net");
    }

    @Override
    public String getSelectorContenido() {
        // Intenta capturar el contenido principal o fallback genéricos
        return "div.skn-chp-chapter-content, div.chapter-content, div.entry-content";
    }

    @Override
    public String getSelectorTitulo() {
        return "h1, .skn-chp-chapter-title";
    }

    @Override
    public String getSelectorContenedorIndice() {
    // Debe incluir mat-expansion-panel para que Selenium espere a los volúmenes
    return "div.skn-nvl-chp-element, mat-expansion-panel";
}

    @Override
    public String getSelectorElementoLista() {
        // Captura la fila completa del capítulo O directamente el enlace si la estructura falla
        return "div.skn-nvl-chp-element, a[href*='/capitulo/']";
    }

    @Override
    public String getSelectorEnlaceCapitulo() {
        // Busca cualquier enlace que tenga '/capitulo/' en su ruta (muy seguro)
        return "a";
    }

    @Override
    public String getSelectorNumeroCapitulo() {
        // Selector específico para el número, si falla, IndiceExtractor usará el texto del enlace
        return "div.skn-nvl-chp-element-chp-number-index";
    }

    /**
     * Estrategia TURBO de SkyNovels (ADR-004): antes vivía dentro de {@code Scraper} tras un
     * {@code if (url.contains("skynovels.net"))}. Es una SPA Angular con volúmenes en acordeones
     * y scroll virtual; se recorre cada volumen ejecutando un script asíncrono dentro de Chrome
     * que hace scroll y acumula los enlaces de capítulo. Si el navegador muere a mitad, se salva
     * lo capturado hasta ese momento.
     */
    @Override
    public String extraerIndiceHtml(WebDriver driver, JavascriptExecutor js, WebDriverWait wait) {
        // Acumulador para poder SALVAR lo capturado si el navegador muere a mitad del índice.
        Set<String> enlacesGuardados = new LinkedHashSet<>();
        try {
            prepararIndice(driver, js, wait);

            log.debug("Modo SkyNovels: Extracción asíncrona iniciada...");

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
                "var maxIter = 400; var iter = 0;" + // cinturón de seguridad: nunca scroll infinito
                "var timer = setInterval(function() {" +
                "    iter++; if (iter > maxIter) { clearInterval(timer); callback(enlaces); return; }" +
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
                "}, 250);"; // cadencia más suave para no saturar el renderer

            List<WebElement> paneles = driver.findElements(By.cssSelector("mat-expansion-panel-header"));

            if (!paneles.isEmpty()) {
                log.debug("Se detectaron {} volúmenes. Analizando a velocidad extrema...", paneles.size());
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

                    log.debug(" -> Escaneando Volumen {}...", (i + 1));

                    // Aquí llamamos al Turbo Script y Java espera pacientemente (en milisegundos)
                    @SuppressWarnings("unchecked")
                    List<String> enlacesExtraidosJS = (List<String>) js.executeAsyncScript(jsTurbo);

                    if (enlacesExtraidosJS != null) {
                        enlacesGuardados.addAll(enlacesExtraidosJS);
                        log.debug("  -> En memoria: {}", enlacesGuardados.size());
                    }

                    // Colapsar el volumen ya procesado para liberar su DOM (memoria)
                    // antes de abrir el siguiente; así solo hay uno expandido a la vez.
                    try {
                        js.executeScript("arguments[0].click();", panel);
                        Thread.sleep(150);
                    } catch (Exception e) {
                        // Si el panel ya no es válido, continuar con el siguiente volumen.
                    }
                }
            } else {
                log.debug("No se detectaron volúmenes agrupados. Extrayendo lista general con Turbo...");
                @SuppressWarnings("unchecked")
                List<String> enlacesExtraidosJS = (List<String>) js.executeAsyncScript(jsTurbo);
                if (enlacesExtraidosJS != null) enlacesGuardados.addAll(enlacesExtraidosJS);
            }

            log.debug("¡Escaneo completo! Total de capítulos capturados en memoria: {}", enlacesGuardados.size());

            // Creamos el HTML "limpio" y perfecto para que Jsoup no se confunda
            return reconstruirDomDesdeEnlaces(enlacesGuardados);

        } catch (Exception e) {
            log.warn("Advertencia obteniendo índice: {}", e.getMessage());
            // Si ya capturamos enlaces, salvarlos en vez de petar con un navegador muerto.
            if (!enlacesGuardados.isEmpty()) {
                log.info("Salvando {} capítulos capturados antes del fallo.", enlacesGuardados.size());
                return reconstruirDomDesdeEnlaces(enlacesGuardados);
            }
            // Último recurso: intentar el HTML, pero sin que un navegador muerto vuelva a petar.
            try {
                return driver.getPageSource();
            } catch (Exception e2) {
                log.warn("No se pudo obtener el HTML tras el fallo: {}", e2.getMessage());
                return "<!DOCTYPE html><html><body></body></html>";
            }
        }
    }

    /** Arma un HTML mínimo y bien formado a partir de los enlaces de capítulo capturados. */
    String reconstruirDomDesdeEnlaces(Collection<String> enlaces) {
        StringBuilder dom = new StringBuilder();
        dom.append("<!DOCTYPE html><html><head><title>SkyNovels Limpio</title></head><body>\n");
        dom.append("<mat-expansion-panel>\n");
        for (String enlaceHtml : enlaces) {
            dom.append("<div class=\"skn-nvl-chp-element\">");
            dom.append(enlaceHtml);
            dom.append("</div>\n");
        }
        dom.append("</mat-expansion-panel>\n</body></html>");
        return dom.toString();
    }
}
