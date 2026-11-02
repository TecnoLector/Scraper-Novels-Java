package com.extractor.mi_extractor.epub.service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class CalibreProcessor {

    private static final Logger log = LoggerFactory.getLogger(CalibreProcessor.class);

    /** Ruta al ejecutable de Calibre (app.calibre.path). Vacía = conversión desactivada. */
    private final String calibrePath;

    public CalibreProcessor(@Value("${app.calibre.path:}") String calibrePath) {
        this.calibrePath = calibrePath;
    }

    /**
     * Convierte el EPUB a v3 con Calibre.
     *
     * @return true si terminó OK; false si Calibre no está disponible o la conversión falló
     *         (en ese caso deja un mensaje "ERROR: ..." en {@code estados}).
     */
    public boolean convertir(String id, Path input, Path output, Map<String, String> estados) {
        if (calibrePath == null || calibrePath.isBlank() || !new File(calibrePath).exists()) {
            log.warn("Calibre no disponible (app.calibre.path='{}'). CONVERTIR_V3 desactivado.", calibrePath);
            estados.put(id, "ERROR: Calibre no está instalado o la ruta (app.calibre.path) es inválida.");
            return false;
        }
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    calibrePath, input.toString(), output.toString(), "--epub-version", "3");
            pb.redirectErrorStream(true);
            Process proceso = pb.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(proceso.getInputStream()))) {
                String linea;
                while ((linea = reader.readLine()) != null) {
                    if (linea.contains("%")) {
                        estados.put(id, "Calibre: " + linea.trim());
                    }
                }
            }
            int codigo = proceso.waitFor();
            if (codigo != 0) {
                log.warn("Calibre terminó con código {}", codigo);
                estados.put(id, "ERROR: Calibre falló (código " + codigo + ").");
                return false;
            }
            return true;
        } catch (Exception e) {
            log.error("Fallo ejecutando Calibre", e);
            estados.put(id, "ERROR: no se pudo ejecutar Calibre: " + e.getMessage());
            return false;
        }
    }
}
