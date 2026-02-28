package com.extractor.mi_extractor.controller;

import com.extractor.mi_extractor.service.EpubService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/epub")
public class EpubController {

    private final EpubService epubService;

    public EpubController(EpubService epubService){
        this.epubService = epubService;
    }

    @PostMapping("/upload")
    public String upload(
        @RequestParam("file") MultipartFile file,
        @RequestParam(value = "files", required = false) MultipartFile[] folderFiles,
        @RequestParam("accion") String accion,
        @RequestParam(value = "tipoDivision", required = false) Integer tipoDivision,
        @RequestParam(value = "parametro", required = false) Integer parametro,
        @RequestParam(value = "sitio", required = false) String sitio,
        @RequestParam(value = "creador", required = false) String creador,
        @RequestParam(value = "nombresPaginas", required = false) List<String> nombresPaginas,
        @RequestParam(value = "capitulosAnteriores", required = false) List<Integer> capitulosAnteriores)throws Exception {
    
    String id = UUID.randomUUID().toString();
    
    // Le pasamos todo al servicio
    epubService.iniciarProceso(id, file.getBytes(), file.getOriginalFilename(), accion, tipoDivision,
     parametro, sitio, creador, nombresPaginas, capitulosAnteriores, folderFiles);
    
    return id;
}

    @GetMapping("/status/{id}")
    public String checkStatus(@PathVariable String id) {
        return epubService.getEstado(id);
    }

    @GetMapping("/download/{id}")
    public ResponseEntity<Resource> download(@PathVariable String id) throws Exception {
        Path path = epubService.getRutaFinal(id);
        
        if (path == null) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = new UrlResource(path.toUri());
        
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip")) 
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + path.getFileName().toString() + "\"")
                .body(resource);
    }
}