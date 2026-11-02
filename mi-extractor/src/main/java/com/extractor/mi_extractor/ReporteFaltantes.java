package com.extractor.mi_extractor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Escribe el informe FALTANTES.txt tras una descarga, con dos secciones: capítulos VIP
 * (bloqueados) y fallos reales (no VIP). Extraído de {@link MenuExtractor} sin cambios de
 * comportamiento; lo usan CLI y web.
 */
class ReporteFaltantes {

    private static final Logger log = LoggerFactory.getLogger(ReporteFaltantes.class);

    private ReporteFaltantes() {
    }

    /** Escribe FALTANTES.txt con dos secciones: VIP (bloqueados) y fallos reales (no VIP). */
    static void escribir(Path pathBase, Set<Double> faltantes, Set<Double> vip) {
        try {
            StringBuilder sb = new StringBuilder();
            if (!vip.isEmpty()) {
                sb.append("Capitulos VIP (bloqueados, requieren desbloqueo en tu cuenta) (")
                        .append(vip.size()).append("):\n")
                        .append(CapitulosUtil.formatearListaCaps(vip)).append("\n\n");
            }
            if (!faltantes.isEmpty()) {
                sb.append("Capitulos NO descargados por error (NO VIP) (")
                        .append(faltantes.size()).append("):\n")
                        .append(CapitulosUtil.formatearListaCaps(faltantes)).append("\n");
            }
            Files.writeString(pathBase.resolve("FALTANTES.txt"), sb.toString());
        } catch (Exception e) {
            log.warn("No se pudo escribir FALTANTES.txt: {}", e.getMessage());
        }
    }
}
