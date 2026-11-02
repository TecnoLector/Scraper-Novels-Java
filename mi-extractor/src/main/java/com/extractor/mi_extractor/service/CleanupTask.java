package com.extractor.mi_extractor.service;
import com.extractor.mi_extractor.common.StorageService;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
public class CleanupTask {

    private static final Logger log = LoggerFactory.getLogger(CleanupTask.class);

    private final StorageService storage;
    private final JobService jobService;
    private final Duration ttl;

    public CleanupTask(StorageService storage, JobService jobService,
            @Value("${app.storage.tmp-ttl-hours}") long ttlHoras) {
        this.storage = storage;
        this.jobService = jobService;
        this.ttl = Duration.ofHours(ttlHoras);
    }

    @Scheduled(fixedRate = 3600000)
    public void limpiarArchivosTemporales() {
        log.info("🧹 Barriendo temporales con antigüedad > {} h...", ttl.toHours());
        try {
            int carpetas = storage.limpiarTemporalesViejos(ttl);
            int trabajos = jobService.purgarExpirados(ttl);
            log.info("🧹 Limpieza completada: {} carpetas temporales y {} trabajos purgados.", carpetas, trabajos);
        } catch (Exception e) {
            log.warn("Fallo durante la limpieza de temporales: {}", e.getMessage(), e);
        }
    }
}
