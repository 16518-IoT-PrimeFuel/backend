package com.primefuel.fulltank.platform.equipment.interfaces.rest.resources;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ConfigureRefillPolicyResource(@NotNull Long providerId, @NotNull Long fuelProductId,
                                            double threshold, double hysteresis, double targetVolume,
                                            @NotBlank String deliveryAddress, boolean enabled) {
}
