package com.primefuel.fulltank.platform.fleet.interfaces.rest.resources;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** v2 driver input. The provider (tenant) always comes from the principal, never from the body. */
public record DriverInputResource(
        Long userId,
        @NotBlank @Size(max = 80) String firstName,
        @NotBlank @Size(max = 80) String lastName,
        @NotBlank @Size(max = 60) String licenseNumber,
        @NotBlank @Size(max = 30) String phoneNumber,
        @NotBlank @Size(max = 160) String email,
        @Size(max = 30) String status) {
}
