package com.primefuel.fulltank.platform.fleet.interfaces.rest.resources;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** v2 tanker input. The provider (tenant) always comes from the principal, never from the body. */
public record TankerInputResource(
        @NotBlank @Size(max = 20) String licensePlate,
        @NotBlank @Size(max = 80) String brand,
        @NotBlank @Size(max = 80) String model,
        @Positive Double capacity,
        @Size(max = 20) String unit,
        @Size(max = 30) String status) {
}
