package com.extractor.mi_extractor;

import java.util.ArrayList;
import java.util.List;

public class GestorSitios {
    
    private static final List<SitioWebConfig> SITIOS_REGISTRADOS = new ArrayList<>();

    static {
        SITIOS_REGISTRADOS.add(new SitioNovelasLigera());
        SITIOS_REGISTRADOS.add(new SitioTuNovelaLigera());
        SITIOS_REGISTRADOS.add(new SitioSkyNovels());
    }

    public static SitioWebConfig obtenerConfig(String url) {
        for (SitioWebConfig sitio : SITIOS_REGISTRADOS) {
            if (sitio.soportaUrl(url)) {
                return sitio;
            }
        }
        System.out.println("⚠️ Web no reconocida. Usando configuración por defecto.");
        return new SitioSkyNovels();
    }
}