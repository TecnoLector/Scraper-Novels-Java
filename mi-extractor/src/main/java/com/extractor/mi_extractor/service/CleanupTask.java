package com.extractor.mi_extractor.service;

import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
public class CleanupTask {

    @Scheduled(fixedRate = 3600000)
    public void limpiarArchivosTemporales() {
        System.out.println("🧹 Limpiando archivos temporales viejos...");
    }
}