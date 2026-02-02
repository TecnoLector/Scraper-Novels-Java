package com.extractor.mi_extractor;

import java.util.ArrayList;
import java.util.List;

public class GestorSitios {
    
    private static List<SitioWebConfig> sitiosRegistrados = new ArrayList<>();
    static {
        sitiosRegistrados.add(new SitioNovelasLigera());
        sitiosRegistrados.add(new SitioSkyNovels()); 
        
        // sitiosRegistrados.add(0, new SitioTuManga());
    }

    public static SitioWebConfig obtenerConfig(String url) {
        for (SitioWebConfig sitio : sitiosRegistrados) {
            if (sitio.esSoportado(url)) {
                System.out.println("Web detectada: " + sitio.getNombreSitio());
                return sitio;
            }
        }
        return null;
    }
}