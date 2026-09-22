package com.primefuel.fulltank.platform.supply.interfaces.rest.resources;

public record ProductResource(
        Long fuelProductId,
        String name,
        String fuelType,
        String unit,
        double pricePerUnit,
        double stock,
        boolean active) {
}
