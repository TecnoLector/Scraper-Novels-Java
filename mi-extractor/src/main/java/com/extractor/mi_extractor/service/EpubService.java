package com.extractor.mi_extractor.service;

import com.extractor.mi_extractor.MenuExtractor;
import com.extractor.mi_extractor.processor.CalibreProcessor;
import com.extractor.mi_extractor.processor.InsertPageBookProcessor;
import com.extractor.mi_extractor.processor.ZipArchiveProcessor;
import com.extractor.mi_extractor.processor.SplitProcessor;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class EpubService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(EpubService.class);

    private final StorageService storage;
    private final CalibreProcessor calibre;
    private final SplitProcessor splitProcessor;
    private final ZipArchiveProcessor zipProcessor;
    private final InsertPageBookProcessor insertPageBookProcessor;
    private final JobService jobService;

    public EpubService(StorageService storage, CalibreProcessor calibre, ZipArchiveProcessor zipProcessor,
            SplitProcessor splitProcessor, InsertPageBookProcessor insertPageBookProcessor, JobService jobService) {
        this.storage = storage;
        this.calibre = calibre;
        this.zipProcessor = zipProcessor;
        this.splitProcessor = splitProcessor;
        this.insertPageBookProcessor = insertPageBookProcessor;
        this.jobService = jobService;
    }

    /**
     * Puente para los processors (legado) que reportan progreso por un Map: cada put
     * se enruta al JobService. Así no hay que cambiar sus firmas.
     */
    private final Map<String, String> puenteProgreso = new ConcurrentHashMap<>() {
        @Override
        public String put(String id, String mensaje) {
            jobService.actualizarMensaje(id, mensaje);
            return mensaje;
        }
    };

    public void iniciarProceso(String id, byte[] bytes, String nombre, String accion,
            Integer tipoDivision, Integer parametro, String sitio, String creador, List<String> nombresPaginas,
            List<Integer> capitulosAnteriores, MultipartFile[] folderFiles) {
        jobService.crear(id, accion);
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
                                puenteProgreso);

                        jobService.setResultado(id, outputSplit);
                        jobService.actualizarMensaje(id, "LISTO");
                        break;

                    case "DESCOMPRIMIR":
                        Path outputZip = dir.resolve(nombre.replace(".epub", "_extraido.zip"));
                        zipProcessor.descomprimirEpub(id, input, outputZip, puenteProgreso);
                        jobService.setResultado(id, outputZip);
                        break;

                    case "REEMPAQUETAR":
                        jobService.actualizarMensaje(id, "Procesando archivo contenedor...");
                        Path carpetaFuente = storage.crearCarpetaTemporal("repack_");

                        zipProcessor.descomprimirZip(input, carpetaFuente);

                        String homeUsuario = System.getProperty("user.home");
                        Path rutaDescargas = Paths.get(homeUsuario, "Downloads");

                        String nombreBase = nombre.contains(".") ? nombre.substring(0, nombre.lastIndexOf('.'))
                                : nombre;
                        Path epubSalida = rutaDescargas.resolve(nombreBase + "_reempaquetado.epub");

                        zipProcessor.reempaquetarComoEpub(id, carpetaFuente, epubSalida, puenteProgreso);

                        storage.eliminarRecursivo(carpetaFuente);
                        jobService.setResultado(id, epubSalida);
                        break;

                    case "INICIO_LIBRO":
                        String homeUsuarioR = System.getProperty("user.home");
                        Path rutaDescargasR = Paths.get(homeUsuarioR, "Downloads");

                        Path nombreFinal = dir.resolve(nombre.replace(".epub", "_con_separadores.epub"));
                        Path outputInicio = rutaDescargasR.resolve(nombreFinal);

                        String sitv = (sitio != null) ? sitio : "";
                        String creadv = (creador != null) ? creador : "TecnoLector";

                        insertPageBookProcessor.insertarPaginasDeLibro(id, input, outputInicio, sitv, creadv,
                                nombresPaginas, capitulosAnteriores, puenteProgreso);

                        jobService.setResultado(id, outputInicio);
                        jobService.actualizarMensaje(id, "LISTO");
                        break;

                    case "CONVERTIR_V3":
                        Path outputCalibre = dir.resolve(nombre.replace(".epub", "_v3.epub"));
                        calibre.convertir(id, input, outputCalibre, puenteProgreso);
                        jobService.setResultado(id, outputCalibre);
                        jobService.actualizarMensaje(id, "LISTO");
                        break;

                    default:
                        jobService.actualizarMensaje(id, "ERROR: Acción no reconocida.");
                        break;
                }
            } catch (Exception e) {
                jobService.actualizarMensaje(id, "ERROR_SISTEMA");
                log.error("Fallo procesando el trabajo {} (accion={})", id, accion, e);
            }
        }).start();
    }

    public void iniciarExtraccionWeb(String id, String url, Integer opcion, Integer hilos, Integer limite, Integer inicio, Integer fin, String lista) {
        jobService.crear(id, "SCRAPE");
        jobService.actualizarMensaje(id, "5% - Inicializando motor multihilo...");

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
                        jobService.actualizarMensaje(id, mensaje);
                    }
                );

                if (carpetaResultante != null) {
                    jobService.actualizarMensaje(id, "98% - Comprimiendo resultado...");

                    Path zipFinal = storage.crearCarpetaTemporal("final_").resolve("Novela_" + id.substring(0, 5) + ".zip");
                    zipProcessor.empaquetarEnZipEstandar(carpetaResultante, zipFinal);

                    jobService.setResultado(id, zipFinal);
                    jobService.actualizarMensaje(id, "LISTO");
                }
            } catch (Exception e) {
                jobService.actualizarMensaje(id, "ERROR: " + e.getMessage());
                log.error("Fallo en la extracción web del trabajo {}", id, e);
            }
        });
    }
}
