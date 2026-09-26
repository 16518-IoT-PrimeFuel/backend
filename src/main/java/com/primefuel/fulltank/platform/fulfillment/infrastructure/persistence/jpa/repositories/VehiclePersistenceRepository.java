package com.primefuel.fulltank.platform.fulfillment.infrastructure.persistence.jpa.repositories;

import com.primefuel.fulltank.platform.fulfillment.infrastructure.persistence.jpa.entities.VehiclePersistenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface VehiclePersistenceRepository extends JpaRepository<VehiclePersistenceEntity, Long> {
    List<VehiclePersistenceEntity> findByProviderId(Long providerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from VehiclePersistenceEntity v where v.id = :id")
    Optional<VehiclePersistenceEntity> findByIdForUpdate(Long id);
}
