package com.extractor.mi_extractor.processor;

import io.documentnode.epub4j.domain.*;
import io.documentnode.epub4j.epub.EpubReader;
import io.documentnode.epub4j.epub.EpubWriter;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Component
public class SplitProcessor {

    private final ZipArchiveProcessor zipProcessor;

    public SplitProcessor(ZipArchiveProcessor zipProcessor) {
        this.zipProcessor = zipProcessor;
    }

    public void procesarDivision(String id, Path inputOriginal, Path outputZip, String nombreBase, 
                                 int opcionSplit, int parametroNumerico, String sitio, String creador, 
                                 Map<String, String> estados) {
        try {
            estados.put(id, "Cargando EPUB original...");
            Book libroOriginal = new EpubReader().readEpub(new FileInputStream(inputOriginal.toFile()));

            // Crear una carpeta temporal para guardar los pedazos antes de comprimirlos
            Path tempDirPedazos = Files.createTempDirectory("pedazos_");

            estados.put(id, "Dividiendo libro...");
            
            // Lógica de ruteo
            switch (opcionSplit) {
                case 1: // Por Rango (Ej. param=50 significa inicio=1, fin=50 para este ejemplo simplificado)
                    dividirPorRango(libroOriginal, nombreBase, 1, parametroNumerico, tempDirPedazos, sitio, creador, id, estados);
                    break;
                case 2: // N Partes
                    dividirEnNPartes(libroOriginal, nombreBase, parametroNumerico, tempDirPedazos, sitio, creador, id, estados);
                    break;
                case 3: // Por Chunks
                    dividirPorTamanoChunk(libroOriginal, nombreBase, parametroNumerico, tempDirPedazos, sitio, creador, id, estados);
                    break;
                // La opción 4 requiere una lógica especial de frontend (enviar lista de rangos), la omitimos temporalmente para simplificar.
            }

            // Comprimir todos los pedazos en el ZIP final
            estados.put(id, "Empaquetando capítulos en archivo ZIP...");
            zipProcessor.empaquetarEnZipEstandar(tempDirPedazos, outputZip);
            
            estados.put(id, "LISTO");
        } catch (Exception e) {
            estados.put(id, "ERROR fatal: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void dividirPorRango(Book libroOriginal, String nombreBase, int inicio, int fin, Path dirSalida, String sitio, String creador, String id, Map<String, String> estados) throws IOException {
        int startIndex = inicio - 1;
        int endIndex = fin - 1;
        String nombreNuevo = nombreBase + "_Cap" + inicio + "-" + fin + ".epub";
        Path rutaNuevo = dirSalida.resolve(nombreNuevo);
        
        estados.put(id, "Creando pedazo: " + nombreNuevo);
        crearEpubDividido(libroOriginal, startIndex, endIndex, 1, 1, rutaNuevo, sitio, creador);
    }

    private void dividirEnNPartes(Book libroOriginal, String nombreBase, int numPartes, Path dirSalida, String sitio, String creador, String id, Map<String, String> estados) throws IOException {
        int totalSecciones = libroOriginal.getSpine().size();
        int seccionesPorParteBase = (int) Math.ceil((double) totalSecciones / numPartes);
        int indiceActual = 0;

        for (int i = 1; i <= numPartes; i++) {
            int startIndex = indiceActual;
            int endIndex = Math.min(indiceActual + seccionesPorParteBase - 1, totalSecciones - 1);
            String nombreParte = nombreBase + "_Parte" + String.format("%02d", i) + ".epub";
            Path rutaParte = dirSalida.resolve(nombreParte);

            estados.put(id, (i*100/numPartes) + "% Creando Parte " + i + "...");
            crearEpubDividido(libroOriginal, startIndex, endIndex, i, numPartes, rutaParte, sitio, creador);
            
            indiceActual = endIndex + 1;
            if (indiceActual >= totalSecciones) break;
        }
    }

    private void dividirPorTamanoChunk(Book libroOriginal, String nombreBase, int chunkSize, Path dirSalida, String sitio, String creador, String id, Map<String, String> estados) throws IOException {
        int totalSecciones = libroOriginal.getSpine().size();
        int numArchivos = (int) Math.ceil((double) totalSecciones / chunkSize);
        int indiceActual = 0;

        for (int i = 1; i <= numArchivos; i++) {
            int startIndex = indiceActual;
            int endIndex = Math.min(indiceActual + chunkSize - 1, totalSecciones - 1);
            String nombreParte = nombreBase + "_Chunk" + String.format("%02d", i) + ".epub";
            Path rutaParte = dirSalida.resolve(nombreParte);

            estados.put(id, (i*100/numArchivos) + "% Creando Trozo " + i + "...");
            crearEpubDividido(libroOriginal, startIndex, endIndex, i, numArchivos, rutaParte, sitio, creador);
            
            indiceActual = endIndex + 1;
            if (indiceActual >= totalSecciones) break;
        }
    }

    private void crearEpubDividido(Book libroOriginal, int startIndex, int endIndex,
            int partNum, int totalParts, Path rutaDestino,
            String sitioExtraccion, String creadorArchivo) throws IOException {

        Book libroDividido = new Book();

        libroDividido.setMetadata(new Metadata());
        libroDividido.getMetadata().setAuthors(libroOriginal.getMetadata().getAuthors());
        libroDividido.getMetadata().setIdentifiers(libroOriginal.getMetadata().getIdentifiers());
        libroDividido.getMetadata().setLanguage(libroOriginal.getMetadata().getLanguage());
        libroDividido.getMetadata().setSubjects(libroOriginal.getMetadata().getSubjects()); // Géneros
        libroDividido.getMetadata().setDescriptions(libroOriginal.getMetadata().getDescriptions()); // Sinopsis

        String tituloOriginal = libroOriginal.getTitle();
        if (tituloOriginal == null || tituloOriginal.isEmpty())
            tituloOriginal = "Libro sin Título";
        String nuevoTitulo = tituloOriginal + " (Parte " + partNum + " de " + totalParts + ")";
        libroDividido.getMetadata().addTitle(nuevoTitulo);

        Resource coverImageResource = libroOriginal.getCoverImage();
        if (coverImageResource != null) {
            byte[] coverData;
            try (InputStream is = coverImageResource.getInputStream()) {
                coverData = is.readAllBytes();
            }
            Resource newCoverResource = new Resource(coverData, coverImageResource.getHref());
            newCoverResource.setMediaType(coverImageResource.getMediaType());
            // Añadir el recurso al nuevo libro
            libroDividido.getResources().add(newCoverResource);
            // Establecerlo como portada
            libroDividido.setCoverImage(newCoverResource);
            System.out.println(" -> Portada copiada.");
        } else {
            System.out.println(" -> Advertencia: EPUB original sin portada definida.");
        }

        Spine spineOriginal = libroOriginal.getSpine();
        for (Resource res : libroOriginal.getResources().getAll()) {
            boolean esContenido = spineOriginal.getResourceIndex(res) >= 0;
            boolean esPortada = res.equals(coverImageResource);

            if (!esContenido && !esPortada) {
                try (InputStream is = res.getInputStream()) {
                    byte[] data = is.readAllBytes();
                    Resource newRes = new Resource(data, res.getHref());
                    newRes.setMediaType(res.getMediaType());
                    libroDividido.getResources().add(newRes);
                    System.out.println(" -> Recurso copiado: " + res.getHref());
                } catch (Exception e) {
                    System.err.println(
                            "Advertencia: No se pudo copiar el recurso " + res.getHref() + ": " + e.getMessage());
                }
            }
        }

        int primerCapitulo = startIndex + 1;
        int ultimoCapitulo = endIndex + 1;
        String htmlPagina = HtmlGeneratorUtil.generarPaginaMetadatosXHTML(
        libroOriginal.getMetadata(), primerCapitulo, ultimoCapitulo, sitioExtraccion, creadorArchivo);
        Resource paginaMetaResource = new Resource(htmlPagina.getBytes(StandardCharsets.UTF_8),
                "OEBPS/Text/metadata_page.xhtml");
        paginaMetaResource.setMediaType(MediaTypes.XHTML);
        libroDividido.getResources().add(paginaMetaResource);
        libroDividido.addSection("Información del Libro", paginaMetaResource);
        System.out.println(" -> Página de metadatos generada.");

        List<Resource> spineResourcesOriginal = libroOriginal.getSpine().getSpineReferences().stream()
                .map(ref -> ref.getResource())
                .collect(Collectors.toList());

        for (int i = startIndex; i <= endIndex; i++) {
            if (i < spineResourcesOriginal.size()) {
                Resource contentResourceOriginal = spineResourcesOriginal.get(i);

                TOCReference tocRef = findTocRefByResource(libroOriginal.getTableOfContents().getTocReferences(),
                        contentResourceOriginal);
                String tituloSeccion = (tocRef != null) ? tocRef.getTitle() : contentResourceOriginal.getHref();

                byte[] contentData;
                try (InputStream is = contentResourceOriginal.getInputStream()) {
                    contentData = is.readAllBytes();
                }
                Resource newContentResource = new Resource(contentData, contentResourceOriginal.getHref());
                newContentResource.setMediaType(contentResourceOriginal.getMediaType());

                libroDividido.getResources().add(newContentResource);
                libroDividido.addSection(tituloSeccion, newContentResource);

            } else {
                System.err.println("Advertencia: Índice fuera de rango al intentar añadir sección " + (i + 1));
            }
        }
        System.out.println(" -> Añadidas " + (endIndex - startIndex + 1) + " secciones de contenido.");

        System.out.println(" -> Escribiendo EPUB en disco...");
        EpubWriter epubWriter = new EpubWriter();
        try (FileOutputStream fos = new FileOutputStream(rutaDestino.toFile())) {
            epubWriter.write(libroDividido, fos);
        }
        
        System.out.println(" -> ¡Archivo liberado y guardado con éxito!");
    }
    

    private TOCReference findTocRefByResource(
            List<TOCReference> tocReferences,
            Resource resource) {

        if (tocReferences == null || tocReferences.isEmpty()) {
            return null;
        }

        for (TOCReference tocRef : tocReferences) {

            if (resource.equals(tocRef.getResource())) {
                return tocRef; // ¡Encontrado!
            }

            TOCReference foundInChildren = findTocRefByResource(tocRef.getChildren(),
                    resource);

            if (foundInChildren != null) {
                return foundInChildren;
            }
        }

        return null;
    }
    // Helper para comprimir la carpeta en un ZIP
}