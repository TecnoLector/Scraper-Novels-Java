package com.extractor.mi_extractor.service;

import com.extractor.mi_extractor.MenuExtractor;
import com.extractor.mi_extractor.processor.CalibreProcessor;
import com.extractor.mi_extractor.processor.InsertPageBookProcessor;
import com.extractor.mi_extractor.processor.ZipArchiveProcessor;
import com.extractor.mi_extractor.processor.SplitProcessor;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
                        estados.put(id, "Procesando archivo contenedor...");
                        Path carpetaFuente = storage.crearCarpetaTemporal("repack_");

                        zipProcessor.descomprimirZip(input, carpetaFuente);

                        String homeUsuario = System.getProperty("user.home");
                        Path rutaDescargas = Paths.get(homeUsuario, "Downloads");

                        String nombreBase = nombre.contains(".") ? nombre.substring(0, nombre.lastIndexOf('.'))
                                : nombre;
                        Path epubSalida = rutaDescargas.resolve(nombreBase + "_reempaquetado.epub");

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

    private void eliminarCarpetaRecursiva(Path ruta) {
        if (ruta == null || !Files.exists(ruta))
            return;

        try (Stream<Path> walk = Files.walk(ruta)) {
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

    public void iniciarExtraccionWeb(String id, String url, Integer opcion, Integer hilos, Integer limite, Integer inicio, Integer fin, String lista) {
        estados.put(id, "5% - Inicializando motor multihilo...");
        
        CompletableFuture.runAsync(() -> {
            try {
                Path rutaTemp = storage.crearCarpetaTemporal("extraccion_");
                MenuExtractor extractor = new MenuExtractor();
                
                Path carpetaResultante = extractor.descargarWebMultiHilo(
                    opcion, 
                    url,
                    hilos,
                    limite,
                    inicio,
                    fin,
                    lista,
                    rutaTemp.toString(),
                    (mensaje) -> {  
                        estados.put(id, mensaje);
                    }
                );
                
                if (carpetaResultante != null) {
                    estados.put(id, "98% - Comprimiendo resultado...");
                    
                    Path zipFinal = storage.crearCarpetaTemporal("final_").resolve("Novela_" + id.substring(0,5) + ".zip");
                    zipProcessor.empaquetarEnZipEstandar(carpetaResultante, zipFinal);
                    
                    archivosListos.put(id, zipFinal);
                    estados.put(id, "LISTO");
                }
            } catch (Exception e) {
                estados.put(id, "ERROR: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }
}