package com.extractor.mi_extractor.common;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class StorageService {

    /** Raiz unica bajo la que viven todas las carpetas temporales, para poder barrerla con seguridad. */
    private final Path tmpRoot;

    public StorageService(@Value("${app.storage.tmp-dir}") String tmpDir) {
        this.tmpRoot = Paths.get(tmpDir).toAbsolutePath().normalize();
    }

    /** Devuelve la raiz temporal (creandola si hace falta). */
    public Path getTmpRoot() throws IOException {
        Files.createDirectories(tmpRoot);
        return tmpRoot;
    }

    public Path crearCarpetaTemporal(String prefijo) throws IOException {
        return Files.createTempDirectory(getTmpRoot(), prefijo);
    }

    public void guardarArchivo(Path ruta, byte[] bytes) throws IOException {
        Files.write(ruta, bytes);
    }

    /** Borra una carpeta (o archivo) recursivamente; no lanza si no existe. */
    public void eliminarRecursivo(Path ruta) throws IOException {
        if (ruta == null || !Files.exists(ruta)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(ruta)) {
            walk.sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    /**
     * Borra las subcarpetas directas de la raiz temporal cuya ultima modificacion
     * sea mas antigua que el TTL indicado.
     *
     * @return numero de carpetas borradas.
     */
    public int limpiarTemporalesViejos(Duration ttl) throws IOException {
        Path root = getTmpRoot();
        Instant limite = Instant.now().minus(ttl);
        int borradas = 0;
        try (Stream<Path> hijos = Files.list(root)) {
            for (Path hijo : (Iterable<Path>) hijos::iterator) {
                try {
                    Instant modificado = Files.getLastModifiedTime(hijo).toInstant();
                    if (modificado.isBefore(limite)) {
                        eliminarRecursivo(hijo);
                        borradas++;
                    }
                } catch (IOException e) {
                    // Una carpeta que no se puede inspeccionar/borrar no debe cortar el barrido.
                    continue;
                }
            }
        }
        return borradas;
    }
}
