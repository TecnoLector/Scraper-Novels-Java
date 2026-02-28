package com.extractor.mi_extractor.processor;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.Map;

import org.springframework.stereotype.Component;

@Component
public class CalibreProcessor {
    public void convertir(String id, Path input, Path output, Map<String, String> estados) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                "C:\\Program Files\\Calibre2\\ebook-convert.exe", 
                input.toString(), output.toString(), "--epub-version", "3"
            );
            pb.redirectErrorStream(true);
            Process proceso = pb.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(proceso.getInputStream()))) {
                String linea;
                while ((linea = reader.readLine()) != null) {
                    if (linea.contains("%")) estados.put(id, "Calibre: " + linea.trim());
                }
            }
            proceso.waitFor();
        } catch (Exception e) {
            estados.put(id, "ERROR_CALIBRE");
        }
    }
}