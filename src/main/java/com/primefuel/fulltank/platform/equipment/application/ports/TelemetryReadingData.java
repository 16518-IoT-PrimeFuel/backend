package com.primefuel.fulltank.platform.equipment.application.ports;

import java.time.Instant;

public record TelemetryReadingData(
        String eventId,
        String deviceId,
        String channel,
        long sequenceNumber,
        Long tankId,
        String schemaVersion,
        Instant capturedAt,
        Instant receivedAt,
        double levelValue,
        String unit,
        String quality) {
}
