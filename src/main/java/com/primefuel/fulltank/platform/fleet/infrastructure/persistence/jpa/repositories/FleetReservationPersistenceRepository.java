package com.primefuel.fulltank.platform.fleet.infrastructure.persistence.jpa.repositories;

import com.primefuel.fulltank.platform.fleet.domain.model.valueobjects.FleetReservationStatus;
import com.primefuel.fulltank.platform.fleet.infrastructure.persistence.jpa.entities.FleetReservationPersistenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface FleetReservationPersistenceRepository
        extends JpaRepository<FleetReservationPersistenceEntity, Long> {

    List<FleetReservationPersistenceEntity> findByProviderId(Long providerId);

    /** Backed by {@code uk_fleet_reservations_reference}, so at most one row can match. */
    Optional<FleetReservationPersistenceEntity> findByReference(String reference);

    /**
     * Active reservations whose half-open window has already ended at {@code now} ({@code window_end <=
     * now}). This is the read behind the deterministic expiry sweep.
     */
    @Query("select r from FleetReservationPersistenceEntity r "
            + "where r.status = :status and r.windowEnd <= :now")
    List<FleetReservationPersistenceEntity> findActivePastDue(
            @Param("status") FleetReservationStatus status,
            @Param("now") Instant now);

    /**
     * Half-open overlap: an existing reservation overlaps {@code [windowStart, windowEnd)} when it starts
     * before the new window ends and ends after the new window starts. Only {@code ACTIVE} reservations
     * count. Must run inside the lock transaction (U06).
     */
    @Query("select r from FleetReservationPersistenceEntity r "
            + "where r.providerId = :providerId and r.status = :status "
            + "and (r.driverId = :driverId or r.tankerId = :tankerId) "
            + "and r.windowStart < :windowEnd and r.windowEnd > :windowStart")
    List<FleetReservationPersistenceEntity> findActiveOverlapping(
            @Param("providerId") Long providerId,
            @Param("driverId") Long driverId,
            @Param("tankerId") Long tankerId,
            @Param("status") FleetReservationStatus status,
            @Param("windowStart") Instant windowStart,
            @Param("windowEnd") Instant windowEnd);
}
