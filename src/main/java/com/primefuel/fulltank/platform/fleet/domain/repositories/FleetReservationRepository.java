package com.primefuel.fulltank.platform.fleet.domain.repositories;

import com.primefuel.fulltank.platform.fleet.domain.model.aggregates.FleetReservation;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface FleetReservationRepository {

    FleetReservation save(FleetReservation reservation);

    Optional<FleetReservation> findById(Long id);

    List<FleetReservation> findActiveByProvider(Long providerId);

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
