package com.primefuel.fulltank.platform.equipment.application.ports;

import java.time.Instant;

public record DeviceBindingData(
        Long id,
        String deviceId,
        String channel,
        Long tankId,
        Long buyerCompanyId,
        String credentialHash,
        Instant validFrom,
        Instant validUntil,
        String status) {
}
