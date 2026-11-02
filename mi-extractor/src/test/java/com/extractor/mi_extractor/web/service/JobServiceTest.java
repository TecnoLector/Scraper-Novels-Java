package com.extractor.mi_extractor.web.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.file.Path;
import java.time.Duration;

import org.junit.jupiter.api.Test;

import com.extractor.mi_extractor.web.model.EstadoJob;

class JobServiceTest {

    @Test
    void mensajePorDefectoSiNoExiste() {
        JobService js = new JobService();
        assertEquals("ESPERANDO...", js.getMensaje("desconocido"));
        assertNull(js.getResultado("desconocido"));
    }

    @Test
    void actualizarMensajeDejaEnProgreso() {
        JobService js = new JobService();
        js.crear("1", "SCRAPE");
        js.actualizarMensaje("1", "50% - bajando");
        assertEquals("50% - bajando", js.getMensaje("1"));
        assertEquals(EstadoJob.EN_PROGRESO, js.getJob("1").getEstado());
    }

    @Test
    void infiereListoYFallidoDelTexto() {
        JobService js = new JobService();
        js.crear("ok", "x");
        js.actualizarMensaje("ok", "LISTO");
        assertEquals(EstadoJob.LISTO, js.getJob("ok").getEstado());

        js.crear("bad", "x");
        js.actualizarMensaje("bad", "ERROR: algo falló");
        assertEquals(EstadoJob.FALLIDO, js.getJob("bad").getEstado());
    }

    @Test
    void guardaYDevuelveElResultado() {
        JobService js = new JobService();
        js.crear("1", "DIVIDIR");
        js.setResultado("1", Path.of("salida.zip"));
        assertEquals(Path.of("salida.zip"), js.getResultado("1"));
    }

    @Test
    void purgaSoloLosExpirados() {
        JobService js = new JobService();
        js.crear("a", "x");
        js.crear("b", "x");
        // TTL enorme: nada se purga (son recién creados).
        assertEquals(0, js.purgarExpirados(Duration.ofHours(24)));
        // TTL negativo: el límite queda en el futuro, así que todos cuentan como expirados.
        assertEquals(2, js.purgarExpirados(Duration.ofSeconds(-1)));
        assertEquals("ESPERANDO...", js.getMensaje("a"));
    }
}
