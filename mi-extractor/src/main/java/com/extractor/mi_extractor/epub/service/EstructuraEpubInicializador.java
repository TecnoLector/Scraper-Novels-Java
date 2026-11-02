package com.extractor.mi_extractor.epub.service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Crea la estructura base de un EPUB (mimetype, META-INF, OEBPS/...) y copia recursos del
 * JAR (hoja de estilos y fuentes). Extraído de {@link DescargaWebService} sin cambios de
 * comportamiento; lo usan tanto el flujo CLI como el web.
 */
public class EstructuraEpubInicializador {

    private static final Logger log = LoggerFactory.getLogger(EstructuraEpubInicializador.class);

    public static void inicializar(Path rutaBase) throws IOException {
        Path rutaMimetype = rutaBase.resolve("mimetype");

        if (Files.notExists(rutaMimetype)) {
            log.info("Detectada carpeta nueva o incompleta. Creando estructura EPUB base...");

            Files.createDirectories(rutaBase.resolve("META-INF"));
            Path rutaOEBPS = rutaBase.resolve("OEBPS");
            Files.createDirectories(rutaOEBPS);
            Files.createDirectories(rutaOEBPS.resolve("Text"));
            Path rutaStyles = rutaOEBPS.resolve("Styles");
            Path rutaFonts = rutaOEBPS.resolve("Fonts");
            Files.createDirectories(rutaStyles);
            Files.createDirectories(rutaFonts);
            Files.createDirectories(rutaOEBPS.resolve("Images"));
            Files.writeString(rutaMimetype, "application/epub+zip", StandardCharsets.US_ASCII);

            copiarRecursoDesdeJar("/recursos/stylesheet.css", rutaStyles.resolve("stylesheet.css"));

            log.info("Buscando y copiando fuentes (.ttf, .otf)...");
            try {
                String rutaJarFonts = "/recursos/fonts";
                URL urlFonts = EstructuraEpubInicializador.class.getResource(rutaJarFonts);

                if (urlFonts != null) {
                    URI uriFonts = urlFonts.toURI();
                    Path pathFonts;
                    FileSystem fileSystem = null;

                    if (uriFonts.getScheme().equals("jar")) {
                        try {
                            fileSystem = FileSystems.getFileSystem(uriFonts);
                        } catch (java.nio.file.FileSystemNotFoundException e) {
                            fileSystem = FileSystems.newFileSystem(uriFonts, Collections.emptyMap());
                        }
                        pathFonts = fileSystem.getPath(rutaJarFonts);
                    } else {
                        pathFonts = Paths.get(uriFonts);
                    }
                    PathMatcher fontMatcher = FileSystems.getDefault().getPathMatcher("glob:**.{ttf,otf}");
                    try (Stream<Path> paths = Files.walk(pathFonts, 1)) {
                        paths.filter(Files::isRegularFile)
                                .filter(fontMatcher::matches)
                                .forEach(fontPath -> {
                                    String nombreFuente = fontPath.getFileName().toString();
                                    String rutaCompletaEnJar = rutaJarFonts + "/" + nombreFuente;
                                    Path destinoFuente = rutaFonts.resolve(nombreFuente);
                                    copiarRecursoDesdeJar(rutaCompletaEnJar, destinoFuente);
                                });
                    }

                    // Cerrar el sistema de archivos del JAR si lo abrimos nosotros
                    // ¡OJO! No cerrar si lo obtuvimos con getFileSystem, podría ser usado por otros
                    // if (fileSystem != null && uriFonts.getScheme().equals("jar") &&
                    // !FileSystems.getFileSystem(uriFonts).equals(fileSystem)) {
                    // fileSystem.close(); // Comentado por precaución, el cierre automático a veces
                    // da problemas
                    // }

                } else {
                    log.warn("No se encontró la carpeta de recursos '/recursos/fonts'.");
                }

            } catch (URISyntaxException | IOException e) {
                log.warn("Error al intentar listar o copiar fuentes: {}", e.getMessage(), e);
            }

            log.info("¡Estructura EPUB base creada con éxito!");
        } else {
            log.info("Estructura EPUB detectada. Añadiendo capítulos...");
        }
    }

    /**
     * Copia un recurso desde el classpath a una ruta de destino en el disco.
     */
    private static void copiarRecursoDesdeJar(String rutaEnJar, Path rutaDestino) {
        try (InputStream is = EstructuraEpubInicializador.class.getResourceAsStream(rutaEnJar)) {
            if (is == null) {
                log.warn("Recurso no encontrado en el JAR: {}", rutaEnJar);
                return;
            }
            Files.createDirectories(rutaDestino.getParent());
            Files.copy(is, rutaDestino, StandardCopyOption.REPLACE_EXISTING);
            log.info("Recurso copiado a: {}", rutaDestino);
        } catch (IOException | NullPointerException e) {
            log.warn("Error al copiar recurso '{}': {}", rutaEnJar, e.getMessage());
        }
    }
}
