package com.primefuel.fulltank.platform.fulfillment.interfaces.rest.resources;

import com.primefuel.fulltank.platform.fulfillment.domain.model.valueobjects.DeliveryPhysicalState;
import com.primefuel.fulltank.platform.fulfillment.domain.model.valueobjects.DeliveryStatus;

import java.time.Instant;

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
        Instant dispatchedAt,
        Instant startedAt,
        Instant arrivedAt,
        Instant deliveringAt,
        Instant deliveredAt,
        String notes,
        int version) {
}
