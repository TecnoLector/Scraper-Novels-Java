package com.extractor.mi_extractor.service;

import com.extractor.mi_extractor.processor.CalibreProcessor;
import com.extractor.mi_extractor.processor.InsertPageBookProcessor;
import com.extractor.mi_extractor.processor.ZipArchiveProcessor;
import com.extractor.mi_extractor.processor.SplitProcessor;
import com.extractor.mi_extractor.processor.InsertPageBookProcessor;

import com.extractor.mi_extractor.service.StorageService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.nio.file.Path;

@Service
public class EpubService {
    private final StorageService storage;
    private final CalibreProcessor calibre;
    private final SplitProcessor splitProcessor;
    private final ZipArchiveProcessor zipProcessor;
    private final InsertPageBookProcessor insertPageBookProcessor;

    public EpubService(StorageService storage, CalibreProcessor calibre, ZipArchiveProcessor zipProcessor,
            SplitProcessor splitProcessor, InsertPageBookProcessor insertPageBookProcessor) {
        this.storage = storage;
        this.calibre = calibre;
        this.zipProcessor = zipProcessor;
        this.splitProcessor = splitProcessor;
        this.insertPageBookProcessor = insertPageBookProcessor;
    }

    private final Map<String, String> estados = new ConcurrentHashMap<>();
    private final Map<String, Path> archivosListos = new ConcurrentHashMap<>();

    public String getEstado(String id) {
        return estados.getOrDefault(id, "ESPERANDO...");
    }

    public Path getRutaFinal(String id) {
        return archivosListos.get(id);
    }

    public void iniciarProceso(String id, byte[] bytes, String nombre, String accion,
            Integer tipoDivision, Integer parametro, String sitio, String creador, List<String> nombresPaginas,
            List<Integer> capitulosAnteriores, MultipartFile[] folderFiles) {
        new Thread(() -> {
            try {
                Path dir = storage.crearCarpetaTemporal("epub_");
                Path input = dir.resolve(nombre);
                storage.guardarArchivo(input, bytes);

                switch (accion) {

                    case "DIVIDIR":
                        Path outputSplit = dir.resolve(nombre.replace(".epub", "_dividido.zip"));
                        int tipoDiv = (tipoDivision != null) ? tipoDivision : 1;
                        int param = (parametro != null) ? parametro : 1;
                        String sit = (sitio != null) ? sitio : "";
                        String cread = (creador != null) ? creador : "";

                        splitProcessor.procesarDivision(id, input, outputSplit, nombre, tipoDiv, param, sit, cread,
                                estados);

                        archivosListos.put(id, outputSplit);
                        estados.put(id, "LISTO");
                        break;

                    case "DESCOMPRIMIR":
                        Path outputZip = dir.resolve(nombre.replace(".epub", "_extraido.zip"));
                        zipProcessor.descomprimirEpub(id, input, outputZip, estados);
                        archivosListos.put(id, outputZip);
                        break;

                    case "REEMPAQUETAR":
                        estados.put(id, "Preparando estructura de carpetas...");

                        Path carpetaFuente = storage.crearCarpetaTemporal("repack_");

                        if (folderFiles != null) {
                            for (MultipartFile file : folderFiles) {
                                String rutaRelativa = file.getOriginalFilename();

                                if (rutaRelativa != null && rutaRelativa.contains("/")) {
                                    rutaRelativa = rutaRelativa.substring(rutaRelativa.indexOf("/") + 1);
                                }

                                Path destinoArchivo = carpetaFuente.resolve(rutaRelativa);

                                Files.createDirectories(destinoArchivo.getParent());
                                file.transferTo(destinoArchivo.toFile());
                            }
                        }

                        String homeUsuario = System.getProperty("user.home");
                        Path rutaDescargas = Paths.get(homeUsuario, "Downloads");
                        String nombreEpub = carpetaFuente.getFileName().toString() + "_final.epub";
                        Path epubSalida = rutaDescargas.resolve(nombreEpub);

                        zipProcessor.reempaquetarComoEpub(id, carpetaFuente, epubSalida, estados);

                        eliminarCarpetaRecursiva(carpetaFuente);

                        archivosListos.put(id, epubSalida);
                        break;

                    case "INICIO_LIBRO":
                        String homeUsuarioR = System.getProperty("user.home");
                        Path rutaDescargasR = Paths.get(homeUsuarioR, "Downloads");

                        Path nombreFinal = dir.resolve(nombre.replace(".epub", "_con_separadores.epub"));
                        Path outputInicio = rutaDescargasR.resolve(nombreFinal);

                        String sitv = (sitio != null) ? sitio : "";
                        String creadv = (creador != null) ? creador : "TecnoLector";

                        insertPageBookProcessor.insertarPaginasDeLibro(id, input, outputInicio, sitv, creadv,
                                nombresPaginas, capitulosAnteriores, estados);

                        archivosListos.put(id, outputInicio);
                        estados.put(id, "LISTO");
                        break;

                    case "CONVERTIR_V3":
                        Path outputCalibre = dir.resolve(nombre.replace(".epub", "_v3.epub"));
                        calibre.convertir(id, input, outputCalibre, estados);
                        archivosListos.put(id, outputCalibre);
                        estados.put(id, "LISTO");
                        break;

                    default:
                        estados.put(id, "ERROR: Acción no reconocida.");
                        break;
                }
            } catch (Exception e) {
                estados.put(id, "ERROR_SISTEMA");
            }
        }).start();
    }
/**
 * Elimina una carpeta y todo su contenido (archivos y subcarpetas).
 * @param ruta La ruta de la carpeta a eliminar.
 */
private void eliminarCarpetaRecursiva(Path ruta) {
    if (ruta == null || !Files.exists(ruta)) return;

    try (Stream<Path> walk = Files.walk(ruta)) {
        // Convertimos el stream a una lista y la invertimos
        // Esto asegura que borramos los archivos antes que las carpetas
        List<File> archivosABorrar = walk
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .collect(Collectors.toList());

        for (File archivo : archivosABorrar) {
            if (!archivo.delete()) {
                System.err.println("No se pudo borrar: " + archivo.getAbsolutePath());
            }
        }
    } catch (IOException e) {
        System.err.println("Error al limpiar temporales: " + e.getMessage());
    }
}
}