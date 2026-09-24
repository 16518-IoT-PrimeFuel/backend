package com.primefuel.fulltank.platform.fulfillment.infrastructure.persistence.jpa.repositories;

import com.primefuel.fulltank.platform.fulfillment.infrastructure.persistence.jpa.entities.ValveCommandPersistenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ValveCommandJpaRepository extends JpaRepository<ValveCommandPersistenceEntity, Long> {
    boolean existsByCommandId(String commandId);

    Optional<ValveCommandPersistenceEntity> findByCommandId(String commandId);
}
