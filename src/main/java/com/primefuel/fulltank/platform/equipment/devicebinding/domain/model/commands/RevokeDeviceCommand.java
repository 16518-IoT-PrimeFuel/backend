package com.primefuel.fulltank.platform.equipment.devicebinding.domain.model.commands;

import java.time.Instant;

public record RevokeDeviceCommand(Long bindingId, Instant at) {
}
