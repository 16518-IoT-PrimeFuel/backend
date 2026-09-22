package com.primefuel.fulltank.platform.telemetry.domain.model.commands;

import java.time.Instant;

public record IngestTelemetryCommand(
        Integer schemaVersion,
        String deviceId,
        String channel,
        Long sequence,
        Instant capturedAt,
        Double level,
        String unit,
        String token) {
}
