package com.primefuel.fulltank.platform.equipment.api;

import java.time.Instant;
import java.util.Optional;

/**
 * Public seam to attribute a reading to the tank that was legally bound at a given instant. Other
 * modules (telemetry, replenishment) must resolve device ownership through this interface only.
 */
public interface ActiveBinding {

    Optional<BindingSnapshot> activeAt(String deviceId, String channel, Instant instant);

    record BindingSnapshot(
            Long id,
            Long organizationId,
            String deviceId,
            String channel,
            Long tankId,
            Instant validFrom,
            Instant validTo) {
    }
}
