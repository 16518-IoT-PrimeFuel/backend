package com.primefuel.fulltank.platform.fleet.infrastructure.persistence.jpa.repositories;

import com.primefuel.fulltank.platform.fleet.infrastructure.persistence.jpa.entities.DriverPersistenceEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DriverPersistenceRepository extends JpaRepository<DriverPersistenceEntity, Long> {

    List<DriverPersistenceEntity> findByProviderId(Long providerId);

    /**
     * Takes a {@code PESSIMISTIC_WRITE} lock on the driver row (U06: lock the reserved resource, not a global
     * lock). Returns empty when the row does not exist, so the caller can answer 404.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from DriverPersistenceEntity d where d.id = :id")
    Optional<DriverPersistenceEntity> lockById(@Param("id") Long id);
}
