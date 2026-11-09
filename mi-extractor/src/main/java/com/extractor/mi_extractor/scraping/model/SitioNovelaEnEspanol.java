package com.extractor.mi_extractor.scraping.model;

import java.net.URI;
import java.time.Duration;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * novelaenespanol.com (WordPress, tema "ranobe-novels"). A diferencia de los sitios con lista
 * estática, aquí el índice NO está en el DOM: solo salen ~30 "últimos capítulos". La lista
 * completa se sirve por un endpoint AJAX JSON del tema:
 *
 * <pre>POST /wp-content/themes/ranobe-novels/template-parts/category/chapters-query.php
 *   cat_id=&lt;id&gt;&amp;limit=&lt;n&gt;&amp;offset=0&amp;query=</pre>
 *
 * que devuelve TODOS los capítulos de una vez como {@code [{ID, post_name, post_title}, ...]}
 * (sin nonce ni login). Por eso {@link #extraerIndiceHtml} se sobrescribe (como en
 * {@link SitioSkyNovels}) y reconstruye un HTML limpio que
 * {@link com.extractor.mi_extractor.scraping.service.IndiceExtractor} sabe parsear con los
 * selectores de abajo. El número de capítulo sale del texto del título ("... Capítulo 1: ...").
 *
 * <p><b>Wordfence (límite de peticiones):</b> el sitio usa el plugin Wordfence, que ante demasiadas
 * peticiones por minuto bloquea la IP temporalmente (~30 min) devolviendo una página 503 de aviso.
 * No es un reto que se resuelva esperando: si la IP está bloqueada, no hay lista que extraer. Por
 * eso aquí: la petición se hace con el <b>XHR del propio navegador</b> (mismo origen, sesión real);
 * se espera un poco a que la portada cargue (por si va lenta) y se reintenta el XHR si viene vacío;
 * y si en vez de la portada llega la página de bloqueo de Wordfence, se avisa con un mensaje claro
 * (hay que esperar unos minutos) en lugar de un error confuso.
 */
public class SitioNovelaEnEspanol extends SitioWebConfig {

    /** Endpoint del tema que devuelve la lista completa de capítulos (relativo: XHR mismo origen). */
    private static final String ENDPOINT_CAPITULOS =
            "/wp-content/themes/ranobe-novels/template-parts/category/chapters-query.php";

    /** Selector del bloque oculto con el id de categoría (presente cuando la portada real ya cargó). */
    private static final By DATOS_CATEGORIA = By.cssSelector(".js-cat-data, .js-post-data");

    private static final String HTML_VACIO = "<!DOCTYPE html><html><body></body></html>";

    private static final int INTENTOS_AJAX = 3;

    /**
     * Script que corre dentro de Chrome: lee el id de categoría del DOM
     * ({@code .js-cat-data[data-category]} o {@code .js-post-data[data-cat]}) y pide el JSON con un
     * XHR síncrono mismo-origen (lleva la cookie de Cloudflare). Devuelve el texto JSON crudo
     * (o "" si algo falla), y Java lo parsea.
     */
    private static final String JS_PEDIR_CAPITULOS =
            "try {"
          + "  var d = document.querySelector('.js-cat-data') || document.querySelector('.js-post-data');"
          + "  if (!d) return '';"
          + "  var catId = d.getAttribute('data-category') || d.getAttribute('data-cat');"
          + "  if (!catId) return '';"
          + "  var xhr = new XMLHttpRequest();"
          + "  xhr.open('POST', '" + ENDPOINT_CAPITULOS + "', false);"
          + "  xhr.setRequestHeader('Content-Type', 'application/x-www-form-urlencoded; charset=UTF-8');"
          + "  xhr.setRequestHeader('X-Requested-With', 'XMLHttpRequest');"
          + "  xhr.send('cat_id=' + encodeURIComponent(catId) + '&limit=100000&offset=0&query=');"
          + "  if (xhr.status !== 200) return '';"
          + "  return xhr.responseText;"
          + "} catch (e) { return ''; }";

    @Override
    public String getNombreSitio() {
        return "Novela en Español";
    }

    @Override
    public boolean esSoportado(String url) {
        return url != null && url.toLowerCase().contains("novelaenespanol.com");
    }

    @Override
    public String getSelectorContenido() {
        return "div.entry-content";
    }

    @Override
    public String getSelectorTitulo() {
        return "h1";
    }

    @Override
    public String getSelectorContenedorIndice() {
        // Describe el HTML que reconstruimos en extraerIndiceHtml (y, de respaldo, el de la web).
        return "div#sectionCenter";
    }

    @Override
    public String getSelectorElementoLista() {
        return "h3.arh-title";
    }

    @Override
    public String getSelectorEnlaceCapitulo() {
        return "a";
    }

    @Override
    public String getSelectorNumeroCapitulo() {
        return "a";
    }

    // --- Anti rate-limit de Wordfence (ver nota de clase) ---

    @Override
    public int getMaxNavegadoresRecomendado() {
        // Wordfence bloquea por peticiones/min; 3 navegadores (~80/min) dejan margen cómodo.
        return 3;
    }

    @Override
    public long getPausaMinMs() {
        return 600; // respiro corto y aleatorio (600-1500 ms) entre capítulos por hilo.
    }

    @Override
    public long getPausaMaxMs() {
        return 1500;
    }

    @Override
    public boolean esPaginaDeBloqueo(String html) {
        if (html == null) {
            return false;
        }
        String h = html.toLowerCase();
        return h.contains("wordfence")
                || h.contains("your access to this site has been limited")
                || h.contains("your access to this service has been limited");
    }

    /**
     * Índice vía AJAX (ver nota de clase): espera a que la portada cargue, pide el JSON con el XHR
     * del navegador reintentando si viene vacío, y reconstruye un HTML limpio. Si llega la página de
     * bloqueo de Wordfence avisa de forma específica; en cualquier otro fallo cae al HTML visible
     * (los ~30 "últimos capítulos" ya vienen en {@code div#sectionCenter}).
     */
    @Override
    public String extraerIndiceHtml(WebDriver driver, JavascriptExecutor js, WebDriverWait wait) {
        String slug = slugDesdeUrl(driver.getCurrentUrl());

        // Esperar a que la portada traiga el id de categoría (por si la página va lenta). Wordfence
        // NO se resuelve esperando, así que la espera es moderada, no larga.
        WebDriverWait esperaPortada = new WebDriverWait(driver, Duration.ofSeconds(12));
        try {
            esperaPortada.until(ExpectedConditions.presenceOfElementLocated(DATOS_CATEGORIA));
        } catch (Exception e) {
            if (pareceBloqueoWordfence(driver)) {
                log.warn("novelaenespanol.com: la IP está bloqueada temporalmente por Wordfence "
                        + "(demasiadas peticiones). Espera unos minutos y reintenta.");
            } else {
                log.warn("novelaenespanol.com: la portada no trajo los datos de categoría; uso el HTML visible.");
            }
            return htmlVisibleORespaldo(driver);
        }

        // Pedir el índice con el XHR del navegador, reintentando: el primer intento puede caer justo
        // cuando Cloudflare aún no fijó la cookie para las peticiones AJAX.
        String json = "";
        for (int intento = 1; intento <= INTENTOS_AJAX && json.isBlank(); intento++) {
            if (intento > 1) {
                dormir(1500);
            }
            Object res = js.executeScript(JS_PEDIR_CAPITULOS);
            json = (res != null) ? res.toString() : "";
            if (json.isBlank()) {
                log.debug("novelaenespanol.com: índice AJAX vacío (intento {}/{}).", intento, INTENTOS_AJAX);
            }
        }

        if (json.isBlank()) {
            log.warn("novelaenespanol.com: el índice AJAX vino vacío tras {} intentos; uso el HTML visible.", INTENTOS_AJAX);
            return htmlVisibleORespaldo(driver);
        }

        try {
            String html = construirIndiceHtml(json, slug);
            if (!html.contains("<a ")) {
                log.warn("novelaenespanol.com: el JSON no traía capítulos; uso el HTML visible.");
                return htmlVisibleORespaldo(driver);
            }
            return html;
        } catch (Exception e) {
            log.warn("novelaenespanol.com: no se pudo parsear el JSON del índice: {}", primeraLinea(e));
            log.debug("Detalle del parseo del índice", e);
            return htmlVisibleORespaldo(driver);
        }
    }

    /** Primer segmento del path de la URL de portada (el slug de la novela), p. ej. "mi-bellisima-presidenta". */
    static String slugDesdeUrl(String url) {
        try {
            String path = new URI(url).getPath();
            if (path != null) {
                for (String seg : path.split("/")) {
                    if (!seg.isBlank()) {
                        return seg;
                    }
                }
            }
        } catch (Exception e) {
            // URL rara: devolvemos cadena vacía y el href saldrá sin slug (mejor que reventar).
        }
        return "";
    }

    /**
     * Reconstruye el HTML del índice a partir del JSON {@code [{ID, post_name, post_title}, ...]}:
     * un {@code <h3 class="arh-title"><a href="/slug/novela-ligera/post_name/">título</a></h3>} por
     * cada item cuyo {@code post_name} sea de capítulo (descarta la propia novela y otras páginas).
     */
    String construirIndiceHtml(String json, String slug) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode arr = mapper.readTree(json);

        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head><meta charset=\"utf-8\"><title>Novela en Español</title></head>")
          .append("<body><div id=\"sectionCenter\">");

        if (arr != null && arr.isArray()) {
            for (JsonNode item : arr) {
                String postName = item.path("post_name").asText("");
                if (!postName.toLowerCase().contains("capitulo")) {
                    continue; // la propia novela y páginas sueltas no llevan "capitulo" en el slug
                }
                String titulo = item.path("post_title").asText("");
                sb.append("<h3 class=\"arh-title\"><a href=\"/")
                  .append(slug).append("/novela-ligera/").append(postName).append("/\">")
                  .append(escapar(titulo))
                  .append("</a></h3>");
            }
        }

        sb.append("</div></body></html>");
        return sb.toString();
    }

    /** Detecta la página de bloqueo de Wordfence en el DOM actual del navegador. */
    private boolean pareceBloqueoWordfence(WebDriver driver) {
        try {
            return esPaginaDeBloqueo(driver.getPageSource());
        } catch (Exception e) {
            return false;
        }
    }

    /** Respaldo: el HTML visible de la página (trae los "últimos capítulos" en div#sectionCenter). */
    private String htmlVisibleORespaldo(WebDriver driver) {
        try {
            return driver.getPageSource();
        } catch (Exception e) {
            log.warn("No se pudo obtener el HTML tras el fallo: {}", primeraLinea(e));
            return HTML_VACIO;
        }
    }

    private static void dormir(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Escape mínimo para incrustar el título en el HTML que luego re-parsea Jsoup. */
    private static String escapar(String t) {
        return t.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** Primera línea del mensaje de una excepción (evita volcar el diagnóstico largo de Selenium). */
    private static String primeraLinea(Throwable t) {
        String msg = (t.getMessage() != null) ? t.getMessage() : t.getClass().getSimpleName();
        int nl = msg.indexOf('\n');
        return (nl >= 0) ? msg.substring(0, nl).trim() : msg.trim();
    }
}
