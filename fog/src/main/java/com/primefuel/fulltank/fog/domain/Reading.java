package com.primefuel.fulltank.fog.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * Lectura del sensor con el mismo contrato que {@code POST /api/telemetry/readings} del backend, para reenviarla
 * sin transformarla.
 */
public record Reading(
        @NotNull Integer schemaVersion,
        @NotBlank @Size(max = 120) String deviceId,
        @NotBlank @Size(max = 60) String channel,
        @NotNull Long sequence,
        @NotNull Instant capturedAt,
        @NotNull Double level,
        @Size(max = 20) String unit) {

    /** Identidad de la lectura para el backend: (dispositivo, canal, secuencia). */
    public String key() {
        return deviceId + "|" + channel + "|" + sequence;
    }
}
