package com.primefuel.fulltank.platform.fulfillment.application.ports;

public interface FleetReservationStore {
    boolean reserve(FleetReservation reservation);
    boolean release(Long providerId, String idempotencyKey);
}
