package com.extractor.mi_extractor.web.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Tests del nombre de novela derivado de la URL para el zip de la web (sin navegador). */
class EpubServiceTest {

    @Test
    void derivaNombreDesdeSlugDeNovela() {
        String n = EpubService.nombreNovelaDesdeUrl(
                "https://www.skynovels.net/novelas/53/el-legendario-asesino-de-magos", "abcdef123");
        assertEquals("El Legendario Asesino De Magos", n);
    }

    @Test
    void ignoraSegmentosNumericos_comoUrlDeCapitulo() {
        String n = EpubService.nombreNovelaDesdeUrl(
                "https://www.skynovels.net/novelas/137/dao-del-inmortal-bizarro/47684/", "abcdef123");
        assertEquals("Dao Del Inmortal Bizarro", n);
    }

    @Test
    void sinSegmentoConLetrasCaeAlFallbackConId() {
        String n = EpubService.nombreNovelaDesdeUrl("https://example.com/12/34", "abcde-xyz");
        assertEquals("Novela_abcde", n);
    }

    @Test
    void urlInvalidaCaeAlFallback() {
        String n = EpubService.nombreNovelaDesdeUrl("no es una url", "abcde99999");
        assertTrue(n.startsWith("Novela_"));
    }
}
