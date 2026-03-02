package com.extractor.mi_extractor.processor;

import org.springframework.stereotype.Component;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@Component
public class ZipArchiveProcessor {

    public void descomprimirEpub(String id, Path inputEpub, Path outputZipResult, Map<String, String> estados) {
        try {
            estados.put(id, "Descomprimiendo EPUB...");
            Path tempExtractDir = Files.createTempDirectory("extract_");
            
            descomprimirZip(inputEpub, tempExtractDir);

            estados.put(id, "Comprimiendo carpeta para descarga...");
            empaquetarEnZipEstandar(tempExtractDir, outputZipResult);
            estados.put(id, "LISTO");

        } catch (Exception e) {
            estados.put(id, "ERROR: " + e.getMessage());
        }
    }

    public void reempaquetarComoEpub(String id, Path carpetaFuente, Path epubSalida, Map<String, String> estados) {
        try {
            estados.put(id, "Validando estructura EPUB...");
            
            Path rutaMimetype = carpetaFuente.resolve("mimetype");
            if (!Files.exists(rutaMimetype)) {
                try (Stream<Path> s = Files.list(carpetaFuente)) {
                    Path posibleSub = s.filter(Files::isDirectory).findFirst().orElse(null);
                    if (posibleSub != null && Files.exists(posibleSub.resolve("mimetype"))) {
                        carpetaFuente = posibleSub;
                        rutaMimetype = carpetaFuente.resolve("mimetype");
                    } else {
                        estados.put(id, "ERROR: Falta el archivo 'mimetype'.");
                        return;
                    }
                }
            }

            estados.put(id, "Re-empaquetando EPUB (Reglas estrictas)...");
            try (FileOutputStream fos = new FileOutputStream(epubSalida.toFile());
                 ZipOutputStream zos = new ZipOutputStream(fos)) {
                
                byte[] mimetypeBytes = Files.readAllBytes(rutaMimetype);
                ZipEntry mimetypeEntry = new ZipEntry("mimetype");
                mimetypeEntry.setMethod(ZipEntry.STORED);
                mimetypeEntry.setSize(mimetypeBytes.length);
                mimetypeEntry.setCompressedSize(mimetypeBytes.length);
                CRC32 crc = new CRC32();
                crc.update(mimetypeBytes);
                mimetypeEntry.setCrc(crc.getValue());
                zos.putNextEntry(mimetypeEntry);
                zos.write(mimetypeBytes);
                zos.closeEntry();

                final Path carpetaBase = carpetaFuente;
                try (Stream<Path> paths = Files.walk(carpetaBase)) {
                    paths.filter(Files::isRegularFile)
                         .filter(path -> !path.getFileName().toString().equals("mimetype"))
                         .forEach(path -> {
                             try {
                                 String zipPath = carpetaBase.relativize(path).toString().replace(File.separator, "/");
                                 
                                 ZipEntry entry = new ZipEntry(zipPath);
                                 entry.setMethod(ZipEntry.DEFLATED);
                                 zos.putNextEntry(entry);
                                 Files.copy(path, zos);
                                 zos.closeEntry();
                             } catch (IOException e) {
                                 System.err.println("Error al añadir archivo: " + e.getMessage());
                             }
                         });
                }
            }
            estados.put(id, "LISTO");
        } catch (Exception e) {
            estados.put(id, "ERROR: " + e.getMessage());
        }
    }

    public void empaquetarEnZipEstandar(Path carpetaFuente, Path archivoZipDestino) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(archivoZipDestino.toFile()))) {
            Files.walk(carpetaFuente)
                 .filter(Files::isRegularFile)
                 .forEach(path -> {
                     try {
                         String rutaLimpia = carpetaFuente.relativize(path).toString().replace("\\", "/");

                         ZipEntry zipEntry = new ZipEntry(rutaLimpia);
                         zos.putNextEntry(zipEntry);
                         Files.copy(path, zos);
                         zos.closeEntry();
                     } catch (IOException e) {
                        System.err.println("Error comprimiendo: " + e.getMessage());
                     }
                 });
        }
    }

    public void descomprimirZip(Path archivoZip, Path carpetaDestino) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(archivoZip.toFile())))) {
            ZipEntry zipEntry;
            byte[] buffer = new byte[1024];
            while ((zipEntry = zis.getNextEntry()) != null) {
                Path nuevoArchivo = carpetaDestino.resolve(zipEntry.getName());
                
                if (!nuevoArchivo.normalize().startsWith(carpetaDestino.normalize())) {
                    continue; 
                }

                if (zipEntry.isDirectory()) {
                    Files.createDirectories(nuevoArchivo);
                } else {
                    Files.createDirectories(nuevoArchivo.getParent());
                    try (FileOutputStream fos = new FileOutputStream(nuevoArchivo.toFile())) {
                        int len;
                        while ((len = zis.read(buffer)) > 0) fos.write(buffer, 0, len);
                    }
                }
                zis.closeEntry();
            }
        }
    }
}