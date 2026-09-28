package com.primefuel.fulltank.platform.telemetry.api.events;

import java.time.Instant;

/**
 * Public contract of the telemetry module: published when a reading is normalised and attributed to a
 * tank. Quarantined readings never reach this point, so consumers can treat it as "validated".
 *
 * <p>It lives in {@code telemetry.api.events} (not in the module's {@code domain}) so other modules can
 * react to it without reaching into telemetry internals — the refill policy (S09) is the first external
 * consumer.
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
