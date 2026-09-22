package com.primefuel.fulltank.platform.fleet.interfaces.rest.resources;

public record TankerV2Resource(
        Long id,
        Long providerId,
        String licensePlate,
        String brand,
        String model,
        Double capacity,
        String unit,
        String status,
        boolean active) {
}
