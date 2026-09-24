package com.primefuel.fulltank.platform.equipment.interfaces.rest.resources;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;

public record IngestTelemetryResource(@NotBlank String deviceId, @NotBlank String channel, long sequenceNumber,
                                      @NotBlank String eventId, @NotBlank String schemaVersion,
                                      Instant capturedAt, double levelValue, @NotBlank String unit,
                                      @NotBlank String quality) {
}
