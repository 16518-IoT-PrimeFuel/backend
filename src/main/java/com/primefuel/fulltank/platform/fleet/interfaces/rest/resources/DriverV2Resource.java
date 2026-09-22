package com.primefuel.fulltank.platform.fleet.interfaces.rest.resources;

public record DriverV2Resource(
        Long id,
        Long providerId,
        Long userId,
        String firstName,
        String lastName,
        String licenseNumber,
        String phoneNumber,
        String email,
        String status,
        boolean active) {
}
