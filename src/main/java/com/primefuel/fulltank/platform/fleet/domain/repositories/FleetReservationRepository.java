package com.primefuel.fulltank.platform.fleet.domain.repositories;

import com.primefuel.fulltank.platform.fleet.domain.model.aggregates.FleetReservation;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface FleetReservationRepository {

    FleetReservation save(FleetReservation reservation);

    Optional<FleetReservation> findById(Long id);

    /** The unique holder of the given idempotency key, whatever its status, or empty if never used. */
    Optional<FleetReservation> findByReference(String reference);

    List<FleetReservation> findActiveByProvider(Long providerId);

    /**
     * Active reservations whose half-open window has already ended at {@code now}. The deterministic input
     * to the expiry sweep; an empty list means nothing to expire.
     */
    List<FleetReservation> findActivePastDue(Instant now);

    /**
     * Active reservations of {@code providerId} that use the given driver or tanker and whose window
     * overlaps {@code [windowStart, windowEnd)}. This is the overlap-revalidation read that must run inside
     * the same transaction that holds the resource lock (U06).
     */
    List<FleetReservation> findActiveOverlapping(Long providerId,
                                                 Long driverId,
                                                 Long tankerId,
                                                 Instant windowStart,
                                                 Instant windowEnd);
}
