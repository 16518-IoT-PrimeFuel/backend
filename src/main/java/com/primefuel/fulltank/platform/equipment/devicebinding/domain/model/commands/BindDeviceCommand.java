package com.primefuel.fulltank.platform.equipment.devicebinding.domain.model.commands;

import java.time.Instant;

public record BindDeviceCommand(
        Long organizationId,
        String deviceId,
        String channel,
        Long tankId,
        Instant validFrom) {
}
