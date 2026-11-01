package com.extractor.mi_extractor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ConfigScraperTest {

    @Test
    void leeElTopeDeNavegadoresDesdeProperties() {
        // application.properties define app.scraper.max-navegadores=10
        assertEquals(10, ConfigScraper.maxNavegadores());
    }

    @Test
    void elSemaforoTieneTantosPermisosComoElTope() {
        assertEquals(ConfigScraper.maxNavegadores(), ConfigScraper.cupo().availablePermits());
    }

    @Test
    void adquirirYLiberarRespetaElConteoDePermisos() throws InterruptedException {
        int antes = ConfigScraper.cupo().availablePermits();
        ConfigScraper.cupo().acquire();
        assertEquals(antes - 1, ConfigScraper.cupo().availablePermits());
        ConfigScraper.cupo().release();
        assertTrue(ConfigScraper.cupo().availablePermits() >= antes);
    }
}
