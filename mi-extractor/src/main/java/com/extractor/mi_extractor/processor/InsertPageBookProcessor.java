package com.extractor.mi_extractor.processor;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import io.documentnode.epub4j.domain.Book;
import io.documentnode.epub4j.domain.MediaTypes;
import io.documentnode.epub4j.domain.Resource;
import io.documentnode.epub4j.domain.SpineReference;
import io.documentnode.epub4j.domain.TOCReference;
import io.documentnode.epub4j.epub.EpubReader;
import io.documentnode.epub4j.epub.EpubWriter;

@Component
public class InsertPageBookProcessor {

    // 1. Cambiamos los parámetros para recibir los datos de la web en vez de Scanner
    public void insertarPaginasDeLibro(String id, Path inputEpub, Path outputEpub, 
                                  String sitioExtraccion, String creadorArchivo, 
                                  List<String> nombresPaginas, List<Integer> capitulosAnteriores, 
                                  Map<String, String> estados) {
        
        Book libroOriginal = null;
        try {
            estados.put(id, "Cargando EPUB original...");
            libroOriginal = new EpubReader().readEpub(new FileInputStream(inputEpub.toFile()));
        } catch (IOException e) {
            estados.put(id, "ERROR fatal al cargar el EPUB: " + e.getMessage());
            return;
        }

        // --- 1. Mapear Capítulos (TU CÓDIGO INTACTO) ---
        List<SpineReference> spineRefs = libroOriginal.getSpine().getSpineReferences();
        int totalSecciones = spineRefs.size();
        Map<Integer, Integer> mapaCapituloAIndice = new HashMap<>();
        Pattern patronNumeroArchivo = Pattern.compile("(?i)(?:Capitulo|Cap|Chapter|Ch)(\\d+)\\.xhtml");
        
        estados.put(id, "Mapeando números de capítulo desde nombres de archivo...");
        int maxCapituloEncontrado = 0;
        
        for (int i = 0; i < totalSecciones; i++) {
            Resource res = spineRefs.get(i).getResource();
            if (res != null && res.getHref() != null) {
                Matcher matcher = patronNumeroArchivo.matcher(res.getHref());
                if (matcher.find()) {
                    try {
                        int numeroCap = Integer.parseInt(matcher.group(1));
                        mapaCapituloAIndice.put(numeroCap, i);
                        if (numeroCap > maxCapituloEncontrado)
                            maxCapituloEncontrado = numeroCap;
                    } catch (NumberFormatException e) {
                        /* Ignorar */ 
                    }
                }
            }
        }
        
        if (mapaCapituloAIndice.isEmpty()) {
            estados.put(id, "ERROR: No se pudo encontrar ningún archivo de capítulo con nombre estándar.");
            return;
        }

        // --- 3. Definir todas las páginas y sus ubicaciones (ADAPTADO A LISTAS WEB) ---
        List<PaginaInsertar> listaPaginas = new ArrayList<>();
        
        if (nombresPaginas != null && capitulosAnteriores != null) {
            for (int i = 0; i < nombresPaginas.size(); i++) {
                String nombreLibro = nombresPaginas.get(i);
                int numCapAnterior = capitulosAnteriores.get(i);

                if (!mapaCapituloAIndice.containsKey(numCapAnterior)) {
                    // Si no encuentra el capítulo, lo omitimos (como hacías con el i--)
                    continue; 
                }

                int indiceEncontrado = mapaCapituloAIndice.get(numCapAnterior);
                PaginaInsertar pagina = new PaginaInsertar(nombreLibro, numCapAnterior);
                pagina.indiceSpineEncontrado = indiceEncontrado;
                listaPaginas.add(pagina);
            }
        }

        // --- 4. Ordenar e Insertar las páginas en el libro (TU CÓDIGO INTACTO) ---
        estados.put(id, "Procesando e insertando " + listaPaginas.size() + " páginas...");
        int insercionesHechas = 0;
        listaPaginas.sort(Comparator.comparingInt(p -> p.indiceSpineEncontrado));

        for (PaginaInsertar pagina : listaPaginas) {
            
            // 2. AQUÍ LLAMAMOS A TU CLASE UTILITY EN LUGAR DEL MÉTODO LOCAL
            String htmlPagina = HtmlGeneratorUtil.generarPaginaTituloLibro(
                    pagina.nombreLibro, sitioExtraccion, creadorArchivo, (insercionesHechas + 1));

            String hrefPagina = "OEBPS/Text/info_libro_" + (insercionesHechas + 1) + ".xhtml";
            Resource res = new Resource(htmlPagina.getBytes(StandardCharsets.UTF_8), hrefPagina);
            res.setMediaType(MediaTypes.XHTML);
            libroOriginal.getResources().add(res);

            int indiceRealInsercion = pagina.indiceSpineEncontrado + 1 + insercionesHechas;
            libroOriginal.getSpine().getSpineReferences().add(indiceRealInsercion, new SpineReference(res));
            TOCReference tocRef = new TOCReference(pagina.nombreLibro, res);
            
            // Prevenir errores si el índice es más corto de lo esperado
            List<TOCReference> tocList = libroOriginal.getTableOfContents().getTocReferences();
            int safeTocIndex = Math.min(indiceRealInsercion, tocList.size());
            tocList.add(safeTocIndex, tocRef);

            estados.put(id, "Insertado '" + pagina.nombreLibro + "'...");
            insercionesHechas++;
        }

        // --- 5. Guardar el nuevo EPUB (ADAPTADO AL SAFE-WRITE WEB) ---
        estados.put(id, "Guardando EPUB final...");
        try {
            EpubWriter epubWriter = new EpubWriter();
            // El try() aquí es vital para cerrar el archivo en Windows
            try (FileOutputStream fos = new FileOutputStream(outputEpub.toFile())) {
                epubWriter.write(libroOriginal, fos);
            }
            
            // 3. SEÑAL WEB: Le avisamos al JavaScript que puede descargar
            estados.put(id, "LISTO");
            
        } catch (IOException e) {
            estados.put(id, "ERROR al guardar el EPUB modificado: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Tu clase interna estática se queda 100% igual
    private static class PaginaInsertar {
        String nombreLibro;
        int numeroCapituloAnterior;
        int indiceSpineEncontrado = -1;

        public PaginaInsertar(String nombreLibro, int numeroCapituloAnterior) {
            this.nombreLibro = nombreLibro;
            this.numeroCapituloAnterior = numeroCapituloAnterior;
        }
    }
}