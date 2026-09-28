package com.primefuel.fulltank.platform.safety.domain.model.valueobjects;

/**
 * The pure outcome of evaluating a position against a {@link com.primefuel.fulltank.platform.safety.domain.model.aggregates.GeofencePolicy}
 * (S17/U09). It mirrors the two domain events the roadmap names — {@code ValveAuthorized} / {@code ValveBlocked}
 * — but is not a physical command: T18 is the ticket that turns an {@link Authorized} decision into a real
 * command/outbox. This type only decides.
 *
 * <p>Fail-closed by construction: there are exactly two outcomes, and the blocked one carries the reason and
 * the evidence (distance/accuracy) that produced it.
 */
public sealed interface GeofenceDecision {

    boolean authorized();

    /** The block reason, or {@code null} when authorized. */
    GeofenceBlockReason reason();

    /** Distance from the position to the geofence centre, or {@code null} when there is no position. */
    Double distanceMeters();

    /** Reported accuracy in metres, or {@code null} when the position has no accuracy. */
    Double accuracyMeters();

    /** The position is fresh, accurate and certainly inside the radius. */
    record Authorized(Double distanceMeters, Double accuracyMeters) implements GeofenceDecision {

        @Override
        public boolean authorized() {
            return true;
        }

        @Override
        public GeofenceBlockReason reason() {
            return null;
        }
    }

    /** The position is missing, stale, inaccurate, or its uncertainty reaches the border. */
    record Blocked(GeofenceBlockReason reason, Double distanceMeters, Double accuracyMeters)
            implements GeofenceDecision {

        @Override
        public boolean authorized() {
            return false;
        }
    }
}
