package com.primefuel.fulltank.platform.equipment.interfaces.rest.resources;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateTankResource(@NotBlank String name,
                                 @NotBlank String fuelType,
                                 @NotNull @Positive Double capacity,
                                 @NotBlank String unit,
                                 @NotNull Double currentLevel) {
}
