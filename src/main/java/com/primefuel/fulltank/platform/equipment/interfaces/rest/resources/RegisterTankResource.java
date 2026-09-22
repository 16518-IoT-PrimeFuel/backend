package com.primefuel.fulltank.platform.equipment.interfaces.rest.resources;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterTankResource(
        @NotNull Long customerAccountId,
        Long siteId,
        @NotBlank @Size(max = 150) String name,
        @Size(max = 30) String fuelType,
        @NotNull Double capacity,
        @Size(max = 20) String unit,
        Double initialLevel) {
}
