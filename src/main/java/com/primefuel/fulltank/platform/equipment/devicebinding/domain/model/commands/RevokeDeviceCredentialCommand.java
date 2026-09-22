package com.primefuel.fulltank.platform.equipment.devicebinding.domain.model.commands;

public record RevokeDeviceCredentialCommand(String deviceId, String channel) {
}
