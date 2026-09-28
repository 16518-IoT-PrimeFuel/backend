package com.primefuel.fulltank.platform.applicationflows;

/**
 * Outcome of a successful assignment: the assigned delivery plus the ids of the exclusive holds taken. It is
 * the assignment's evidence that the acceptance was consumed and both reservations belong to it.
 */
public record AssignDeliveryResult(
        Long deliveryId,
        Long orderId,
        Long providerId,
        Long driverId,
        Long tankerId,
        Long supplyReservationId,
        Long fleetReservationId,
        String physicalState,
        String commandId) {
}
