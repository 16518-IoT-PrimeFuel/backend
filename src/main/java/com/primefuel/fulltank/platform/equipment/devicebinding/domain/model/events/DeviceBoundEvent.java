package com.primefuel.fulltank.platform.equipment.devicebinding.domain.model.events;

import java.time.Instant;

/** No credential or secret is ever carried on a domain event. */
public record DeviceBoundEvent(
        String deviceId,
        String channel,
        Long tankId,
        Long organizationId,
        Instant validFrom) {
}
