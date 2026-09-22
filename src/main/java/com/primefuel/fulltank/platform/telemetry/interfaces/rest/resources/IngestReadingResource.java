package com.primefuel.fulltank.platform.telemetry.interfaces.rest.resources;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record IngestReadingResource(
        @NotNull Integer schemaVersion,
        @NotBlank @Size(max = 120) String deviceId,
        @NotBlank @Size(max = 60) String channel,
        @NotNull Long sequence,
        @NotNull Instant capturedAt,
        @NotNull Double level,
        @Size(max = 20) String unit) {
}
