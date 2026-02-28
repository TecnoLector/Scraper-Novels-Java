package com.extractor.mi_extractor.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.stereotype.Service;

@Service
public class StorageService {
    public Path crearCarpetaTemporal(String prefijo) throws IOException {
        return Files.createTempDirectory(prefijo);
    }

    public void guardarArchivo(Path ruta, byte[] bytes) throws IOException {
        Files.write(ruta, bytes);
    }
}