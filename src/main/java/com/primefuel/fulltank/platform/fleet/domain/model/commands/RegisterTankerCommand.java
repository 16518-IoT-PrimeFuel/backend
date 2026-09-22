package com.primefuel.fulltank.platform.fleet.domain.model.commands;

/** Registers a tanker (the fuel truck) for a provider (the tenant). */
public record RegisterTankerCommand(
        Long providerId,
        String licensePlate,
        String brand,
        String model,
        Double capacity,
        String unit,
        String status) {
}
