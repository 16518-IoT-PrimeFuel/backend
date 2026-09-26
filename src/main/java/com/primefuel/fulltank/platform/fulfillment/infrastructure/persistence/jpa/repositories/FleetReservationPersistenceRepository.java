package com.primefuel.fulltank.platform.fulfillment.infrastructure.persistence.jpa.repositories;

import com.primefuel.fulltank.platform.fulfillment.infrastructure.persistence.jpa.entities.FleetReservationPersistenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;

public interface FleetReservationPersistenceRepository extends JpaRepository<FleetReservationPersistenceEntity, Long> {
    Optional<FleetReservationPersistenceEntity> findByIdempotencyKey(String idempotencyKey);

    @Query("select count(r) from FleetReservationPersistenceEntity r "
            + "where r.status = 'RESERVED' and (r.driverId = :driverId or r.vehicleId = :vehicleId) "
            + "and r.windowStart < :windowEnd and r.windowEnd > :windowStart")
    long countOverlapping(Long driverId, Long vehicleId, Instant windowStart, Instant windowEnd);
}
