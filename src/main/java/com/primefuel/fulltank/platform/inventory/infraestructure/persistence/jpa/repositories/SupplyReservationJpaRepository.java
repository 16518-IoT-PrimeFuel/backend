package com.primefuel.fulltank.platform.inventory.infrastructure.persistence.jpa.repositories;

import com.primefuel.fulltank.platform.inventory.infrastructure.persistence.jpa.entities.SupplyReservationPersistenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupplyReservationJpaRepository extends JpaRepository<SupplyReservationPersistenceEntity, Long> {
    boolean existsByRequestId(Long requestId);
}
