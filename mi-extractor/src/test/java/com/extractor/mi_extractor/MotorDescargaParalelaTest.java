package com.extractor.mi_extractor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.TreeMap;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

/**
 * Valida la clasificación éxito/VIP/fallo del motor SIN abrir Chrome: el Scraper es un mock
 * que devuelve HTML bueno, cartel VIP o timeout según el capítulo. Es la red de seguridad que
 * pide ADR-001 ("lógica testeable sin navegador").
 */
class MotorDescargaParalelaTest {

    /** Callback de progreso que no hace nada (en test no mostramos barras). */
    private static final MotorDescargaParalela.ProgresoDescarga SIN_PROGRESO =
            new MotorDescargaParalela.ProgresoDescarga() {
                @Override
                public void capituloIniciado(int h, double n, int he, int te, int hg, int tg) { }
                @Override
                public void capituloTerminado(int h, double n, int he, int te, int hg, int tg) { }
                @Override
                public void hiloTerminado(int h) { }
            };

    @Test
    void clasificaExitoVipYFalloEnUnSoloHilo() throws Exception {
        Scraper scraper = mock(Scraper.class);
        ExtractorContenido extractor = mock(ExtractorContenido.class);
        EscritorArchivo escritor = mock(EscritorArchivo.class);
        Capitulo capitulo = mock(Capitulo.class);

        // cap 1 -> HTML bueno; cap 2 -> VIP; cap 3 -> timeout (html null) en sus 3 intentos.
        Scraper.ResultadoCapitulo ok = new Scraper.ResultadoCapitulo("<html>cap</html>", false);
        Scraper.ResultadoCapitulo vipRes = new Scraper.ResultadoCapitulo(null, true);
        Scraper.ResultadoCapitulo fallo = new Scraper.ResultadoCapitulo(null, false);
        when(scraper.obtenerContenidoOVip()).thenReturn(ok, vipRes, fallo);
        when(extractor.extraer(any(), any())).thenReturn(capitulo);

        Supplier<Scraper> fabScraper = () -> scraper;
        MotorDescargaParalela motor = new MotorDescargaParalela(fabScraper, () -> extractor, () -> escritor);

        TreeMap<Double, String> caps = new TreeMap<>();
        caps.put(1.0, "http://x/1");
        caps.put(2.0, "http://x/2");
        caps.put(3.0, "http://x/3");

        MotorDescargaParalela.ResultadoLote res = motor.ejecutar(caps, "/ruta/novela", 1, SIN_PROGRESO);

        assertEquals(3, res.total);
        assertEquals(1, res.exitos);
        assertEquals(1, res.vip.size());
        assertTrue(res.vip.contains(2.0));
        assertEquals(1, res.faltantes.size());
        assertTrue(res.faltantes.contains(3.0));

        // Solo el capítulo bueno se guardó, y cap 3 se reintentó (navegamos 1 + 1 + 3 = 5 veces).
        verify(escritor, times(1)).guardarCapitulo(eq(capitulo), eq("/ruta/novela"), eq(1.0));
        verify(scraper, times(5)).obtenerContenidoOVip();
    }

    @Test
    void mapaVacioNoLanzaYDevuelveTotalCero() {
        MotorDescargaParalela motor = new MotorDescargaParalela(
                () -> mock(Scraper.class), () -> mock(ExtractorContenido.class), () -> mock(EscritorArchivo.class));

        MotorDescargaParalela.ResultadoLote res = motor.ejecutar(new TreeMap<>(), "/ruta", 4, SIN_PROGRESO);

        assertEquals(0, res.total);
        assertEquals(0, res.exitos);
        assertTrue(res.vip.isEmpty());
        assertTrue(res.faltantes.isEmpty());
    }
}
