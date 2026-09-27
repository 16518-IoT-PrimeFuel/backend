package com.primefuel.fulltank.platform.fulfillment.interfaces.rest.resources;

import com.primefuel.fulltank.platform.fulfillment.domain.model.valueobjects.DeliveryPhysicalState;
import com.primefuel.fulltank.platform.fulfillment.domain.model.valueobjects.DeliveryStatus;

import java.time.LocalDateTime;

/**
 * Delivery view: the physical state machine alongside the legacy status (compatibility map), the
 * physical timestamps and both volumes.
 */
public record DeliveryResource(
        Long id,
        Long orderId,
        Long providerId,
        Long driverId,
        Long vehicleId,
        DeliveryStatus legacyStatus,
        DeliveryPhysicalState physicalState,
        Double requestedVolume,
        Double deliveredVolume,
        LocalDateTime dispatchedAt,
        LocalDateTime startedAt,
        LocalDateTime arrivedAt,
        LocalDateTime deliveringAt,
        LocalDateTime deliveredAt,
        String notes,
        int version) {
}
