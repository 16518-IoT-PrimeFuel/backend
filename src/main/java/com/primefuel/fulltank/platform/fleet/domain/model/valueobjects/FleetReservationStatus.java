package com.primefuel.fulltank.platform.fleet.domain.model.valueobjects;

/**
 * Lifecycle of a {@code FleetReservation} (S13). {@code RELEASED} and {@code EXPIRED} are terminal; the
 * transitions that produce them (idempotent release, window expiry) are T13-B — T13-A only creates
 * {@code ACTIVE} reservations.
 */
public enum FleetReservationStatus {
    ACTIVE,
    RELEASED,
    EXPIRED
}
