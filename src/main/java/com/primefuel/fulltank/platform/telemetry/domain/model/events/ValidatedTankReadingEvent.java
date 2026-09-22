package com.primefuel.fulltank.platform.telemetry.domain.model.events;

import java.time.Instant;

/**
 * Published when a reading is normalised and attributed to a tank. Quarantined readings never reach this
 * point, so consumers can treat it as "validated".
 */
public record ValidatedTankReadingEvent(
        Long readingId,
        String deviceId,
        String channel,
        long sequence,
        Long tankId,
        Long organizationId,
        double level,
        String unit,
        Instant capturedAt) {
}
