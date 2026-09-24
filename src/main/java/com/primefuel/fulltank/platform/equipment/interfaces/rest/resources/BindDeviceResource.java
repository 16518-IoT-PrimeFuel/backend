package com.primefuel.fulltank.platform.equipment.interfaces.rest.resources;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;

public record BindDeviceResource(@NotBlank String deviceId, @NotBlank String channel,
                                 @NotBlank String credential, Instant validFrom, Instant validUntil) {
}
