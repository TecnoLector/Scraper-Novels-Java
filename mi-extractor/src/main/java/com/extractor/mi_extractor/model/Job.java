package com.extractor.mi_extractor.model;

import java.nio.file.Path;
import java.time.Instant;

/**
 * Trabajo gestionado por el JobService. Mantiene el estado estructurado
 * (enum {@link EstadoJob}) y, a la vez, el {@code mensaje} de texto libre que la
 * interfaz web ya espera en /status (progreso, JSON multihilo, "LISTO", "ERROR: ...").
 */
public class Job {

    private final String id;
    private final String tipo;
    private final Instant createdAt;

    private volatile EstadoJob estado = EstadoJob.PENDIENTE;
    private volatile String mensaje;
    private volatile Path resultado;
    private volatile String error;

    public Job(String id, String tipo) {
        this.id = id;
        this.tipo = tipo;
        this.createdAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public String getTipo() {
        return tipo;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public EstadoJob getEstado() {
        return estado;
    }

    public void setEstado(EstadoJob estado) {
        this.estado = estado;
    }

    public String getMensaje() {
        return mensaje;
    }

    public void setMensaje(String mensaje) {
        this.mensaje = mensaje;
    }

    public Path getResultado() {
        return resultado;
    }

    public void setResultado(Path resultado) {
        this.resultado = resultado;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }
}
