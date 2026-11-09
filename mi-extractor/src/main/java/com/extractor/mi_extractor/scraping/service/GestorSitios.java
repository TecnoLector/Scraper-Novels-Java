package com.extractor.mi_extractor.scraping.service;
import com.extractor.mi_extractor.scraping.model.*;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GestorSitios {

    private static final Logger log = LoggerFactory.getLogger(GestorSitios.class);

    private static final List<SitioWebConfig> SITIOS_REGISTRADOS = new ArrayList<>();

    static {
        SITIOS_REGISTRADOS.add(new SitioNovelasLigera());
        SITIOS_REGISTRADOS.add(new SitioTuNovelaLigera());
        SITIOS_REGISTRADOS.add(new SitioNOVA());
        SITIOS_REGISTRADOS.add(new SitioSkyNovels());
        SITIOS_REGISTRADOS.add(new SitioTranlationsRakuen());
        SITIOS_REGISTRADOS.add(new SitioNovelaEnEspanol());
    }

    public static SitioWebConfig obtenerConfig(String url) {
        for (SitioWebConfig sitio : SITIOS_REGISTRADOS) {
            if (sitio.esSoportado(url)) {
                return sitio;
            }
        }
        log.warn("Web no reconocida. Usando configuración por defecto.");
        return new SitioSkyNovels();
    }

    /** Indica si algún sitio registrado soporta la URL, sin el fallback a SkyNovels. */
    public static boolean esUrlSoportada(String url) {
        if (url == null) {
            return false;
        }
        for (SitioWebConfig sitio : SITIOS_REGISTRADOS) {
            if (sitio.esSoportado(url)) {
                return true;
            }
        }
        return false;
    }
}