package com.primefuel.fulltank.platform.equipment.devicebinding.domain.model.commands;

import java.time.Instant;

public record MoveDeviceCommand(Long bindingId, Long newTankId, Instant at) {
}
