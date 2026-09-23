package com.primefuel.fulltank.platform.applicationflows.interfaces.rest.resources;

/** Response body of a committed assignment: the assigned delivery and the exclusive holds taken. */
public record AssignmentResource(
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
