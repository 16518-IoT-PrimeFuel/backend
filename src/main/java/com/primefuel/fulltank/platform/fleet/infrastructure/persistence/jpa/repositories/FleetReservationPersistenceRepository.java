package com.primefuel.fulltank.platform.fleet.infrastructure.persistence.jpa.repositories;

import com.primefuel.fulltank.platform.fleet.domain.model.valueobjects.FleetReservationStatus;
import com.primefuel.fulltank.platform.fleet.infrastructure.persistence.jpa.entities.FleetReservationPersistenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface FleetReservationPersistenceRepository
        extends JpaRepository<FleetReservationPersistenceEntity, Long> {

    List<FleetReservationPersistenceEntity> findByProviderId(Long providerId);

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
