package com.primefuel.fulltank.platform.replenishment.interfaces.rest.resources;

import io.swagger.v3.oas.annotations.media.Schema;

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
        @Schema(description = "Dirección de entrega guardada en la solicitud.") String deliveryAddress,
        @Schema(description = "Fecha programada de entrega en la fecha de negocio de Lima.") java.time.LocalDate deliveryDate,
        int version) {
}
