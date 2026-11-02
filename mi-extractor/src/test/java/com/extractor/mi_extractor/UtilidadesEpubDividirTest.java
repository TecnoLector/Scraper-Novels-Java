package com.extractor.mi_extractor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Scanner;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.documentnode.epub4j.domain.Author;
import io.documentnode.epub4j.domain.Book;
import io.documentnode.epub4j.domain.Resource;
import io.documentnode.epub4j.epub.EpubReader;
import io.documentnode.epub4j.epub.EpubWriter;

/**
 * Test de caracterización de la división de EPUB por rango (red de seguridad para el troceo
 * de {@link UtilidadesEPUB}). Conduce el menú real por stdin y fija el comportamiento actual:
 * el EPUB resultante lleva una página de metadatos + las secciones del rango, y el título
 * recibe el sufijo "(Parte X de Y)". El mismo test debe seguir pasando tras mover la lógica
 * a su colaborador, porque el movimiento es verbatim.
 */
class UtilidadesEpubDividirTest {

    @TempDir
    Path carpeta;

    private static String capituloXhtml(int n) {
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<!DOCTYPE html>\n"
             + "<html xmlns=\"http://www.w3.org/1999/xhtml\"><head><title>Capítulo " + n + "</title></head>"
             + "<body><h1>Capítulo " + n + "</h1><p>Texto del capítulo " + n + ".</p></body></html>";
    }

    /** Crea en la carpeta de trabajo un EPUB 2 (epub4j) con 4 capítulos y metadatos. */
    private void crearEpubOriginal(Path destino) throws Exception {
        Book libro = new Book();
        libro.getMetadata().addTitle("Mi Novela");
        libro.getMetadata().addAuthor(new Author("Autora", "Ejemplo"));
        libro.getMetadata().setLanguage("es");
        for (int i = 1; i <= 4; i++) {
            libro.addSection("Capítulo " + i,
                    new Resource(capituloXhtml(i).getBytes(StandardCharsets.UTF_8), "Text/cap" + i + ".xhtml"));
        }
        try (FileOutputStream fos = new FileOutputStream(destino.toFile())) {
            new EpubWriter().write(libro, fos);
        }
    }

    @Test
    void dividePorRangoGeneraEpubConPaginaDeMetadatosYSufijoDeParte() throws Exception {
        Path original = carpeta.resolve("Test.epub");
        crearEpubOriginal(original);

        // Guion de entradas del menú:
        // carpeta -> 1 (Dividir) -> Test.epub -> sitio(vacío) -> creador(vacío)
        // -> 1 (por rango) -> inicio=1 -> fin=2
        String guion = String.join("\n",
                carpeta.toString(),
                "1",
                "Test.epub",
                "",
                "",
                "1",
                "1",
                "2") + "\n";

        Scanner sc = new Scanner(new ByteArrayInputStream(guion.getBytes(StandardCharsets.UTF_8)));
        // mostrarMenu() es un bucle sin salida limpia: tras dividir, el siguiente nextInt()
        // agota la entrada y lanza. La división ya ocurrió; ignoramos ese final.
        try {
            UtilidadesEPUB.iniciar(sc);
        } catch (Exception finDeEntrada) {
            // esperado: se acabó el guion de stdin
        }

        Path salida = carpeta.resolve("Test.epub_Rango_1-2").resolve("Test.epub_Cap1-2.epub");
        assertTrue(Files.exists(salida), "debe crear el EPUB dividido en la subcarpeta de rango");

        Book dividido;
        try (FileInputStream fis = new FileInputStream(salida.toFile())) {
            dividido = new EpubReader().readEpub(fis);
        }

        // 1 página de metadatos + 2 capítulos del rango 1-2.
        assertEquals(3, dividido.getSpine().size(), "metadatos + 2 secciones de contenido");
        assertEquals("Mi Novela (Parte 1 de 1)", dividido.getTitle());
        assertEquals("Información del Libro",
                dividido.getTableOfContents().getTocReferences().get(0).getTitle(),
                "la primera sección es la página de metadatos");
    }
}
