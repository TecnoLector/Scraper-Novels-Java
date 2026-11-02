package com.extractor.mi_extractor;

import java.util.Map;
import java.util.TreeMap;

/**
 * Recorre el índice de una novela (paginado incluido) con el {@link Scraper} y devuelve el
 * mapa completo de capítulos (número -> URL). Extraído de {@link MenuExtractor} sin cambios
 * de comportamiento; lo usan CLI y web.
 */
class CatalogoCapitulos {

    private CatalogoCapitulos() {
    }

    static Map<Double, String> obtener(String urlPortada) {
    System.out.println("Obteniendo lista de capítulos...");
    Map<Double, String> mapaTotal = new TreeMap<>();

    Scraper scraper = new Scraper();
    IndiceExtractor extractor = new IndiceExtractor();

    String urlActual = urlPortada;
    int pagina = 1;

    try {
    while (urlActual != null) {
        System.out.println(">>> Analizando índice - Página " + pagina + "...");
        scraper.navegarA(urlActual);

        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
        }

        String htmlIndice = scraper.obtenerHtmlDeIndice();
        if (htmlIndice == null)
            break;

        Map<Double, String> capitulosPagina = extractor.extraerEnlaces(htmlIndice, urlActual);

        if (capitulosPagina.isEmpty()) {
            System.out.println("⚠ No se encontraron capítulos en la página " + pagina + ".");
            if (pagina == 1)
                break;
        } else {
            mapaTotal.putAll(capitulosPagina);
            System.out.println("    -> Encontrados " + capitulosPagina.size() + " caps. (Total acumulado: "
                    + mapaTotal.size() + ")");
        }

        String urlSiguiente = extractor.obtenerSiguientePagina(htmlIndice, urlActual);

        if (urlSiguiente != null && !urlSiguiente.equals(urlActual)) {
            System.out.println(">>> Detectada siguiente página: " + urlSiguiente);
            urlActual = urlSiguiente;
            pagina++;
        } else {
            System.out.println(">>> Fin del índice (No se detectaron más páginas).");
            urlActual = null;
        }
    }
    } finally {
        scraper.cerrar(); // SIEMPRE cerrar, aunque el navegador haya muerto a mitad del índice.
    }
    return mapaTotal;
}
}
