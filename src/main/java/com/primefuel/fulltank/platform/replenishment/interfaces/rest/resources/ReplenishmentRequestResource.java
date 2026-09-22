package com.primefuel.fulltank.platform.replenishment.interfaces.rest.resources;

public record ReplenishmentRequestResource(
        Long id,
        Long organizationId,
        Long customerAccountId,
        Long tankId,
        Long providerId,
        Long fuelProductId,
        double quantity,
        String unit,
        double unitPrice,
        String status,
        String source,
        String rejectionReason,
        Long orderId,
        int version) {
}
