package com.primefuel.fulltank.platform.fulfillment.infrastructure.persistence.jpa.repositories;

import com.primefuel.fulltank.platform.fulfillment.infrastructure.persistence.jpa.entities.DriverPersistenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface DriverPersistenceRepository extends JpaRepository<DriverPersistenceEntity, Long> {
    List<DriverPersistenceEntity> findByProviderId(Long providerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from DriverPersistenceEntity d where d.id = :id")
    Optional<DriverPersistenceEntity> findByIdForUpdate(Long id);
}
