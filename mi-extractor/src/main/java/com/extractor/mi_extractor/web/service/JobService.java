package com.extractor.mi_extractor.web.service;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import com.extractor.mi_extractor.web.model.EstadoJob;
import com.extractor.mi_extractor.web.model.Job;

/**
 * Gestiona los trabajos en memoria con expiración por TTL. Reemplaza a los antiguos
 * mapas sueltos de EpubService. El contrato de /status y /download no cambia:
 * {@link #getMensaje} devuelve el mismo texto que antes y {@link #getResultado} el Path.
 */
@Service
public class JobService {

    private final Map<String, Job> jobs = new ConcurrentHashMap<>();

    /** Registra un nuevo trabajo (estado PENDIENTE, createdAt = ahora). */
    public void crear(String id, String tipo) {
        jobs.put(id, new Job(id, tipo));
    }

    /**
     * Actualiza el mensaje de texto (progreso). Infiere el estado a partir del texto
     * para no romper el contrato que ya usa la web: "LISTO" -> LISTO, "ERROR..." -> FALLIDO.
     */
    public void actualizarMensaje(String id, String mensaje) {
        Job job = jobs.computeIfAbsent(id, k -> new Job(k, "?"));
        job.setMensaje(mensaje);
        if (mensaje == null) {
            return;
        }
        String m = mensaje.trim();
        if (m.equalsIgnoreCase("LISTO")) {
            job.setEstado(EstadoJob.LISTO);
        } else if (m.toUpperCase().startsWith("ERROR")) {
            job.setEstado(EstadoJob.FALLIDO);
            job.setError(m);
        } else {
            job.setEstado(EstadoJob.EN_PROGRESO);
        }
    }

    /** Asocia el archivo resultado al trabajo (sin tocar el mensaje). */
    public void setResultado(String id, Path resultado) {
        Job job = jobs.computeIfAbsent(id, k -> new Job(k, "?"));
        job.setResultado(resultado);
    }

    /** Mensaje de texto para /status (igual que antes: "ESPERANDO..." si no existe). */
    public String getMensaje(String id) {
        Job job = jobs.get(id);
        return (job != null && job.getMensaje() != null) ? job.getMensaje() : "ESPERANDO...";
    }

    /** Ruta del resultado para /download (null si no hay). */
    public Path getResultado(String id) {
        Job job = jobs.get(id);
        return (job != null) ? job.getResultado() : null;
    }

    public Job getJob(String id) {
        return jobs.get(id);
    }

    /**
     * Elimina los trabajos cuya antigüedad supere el TTL (evita que el mapa crezca sin límite).
     *
     * @return número de trabajos purgados.
     */
    public int purgarExpirados(Duration ttl) {
        Instant limite = Instant.now().minus(ttl);
        int purgados = 0;
        for (Job job : jobs.values()) {
            if (job.getCreatedAt().isBefore(limite)) {
                jobs.remove(job.getId());
                purgados++;
            }
        }
        return purgados;
    }
}
