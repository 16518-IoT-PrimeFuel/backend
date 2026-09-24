package com.primefuel.fulltank.platform.equipment.infrastructure.persistence.jpa.repositories;

import com.primefuel.fulltank.platform.equipment.infrastructure.persistence.jpa.entities.RefillPolicyPersistenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RefillPolicyJpaRepository extends JpaRepository<RefillPolicyPersistenceEntity, Long> {
    Optional<RefillPolicyPersistenceEntity> findByTankId(Long tankId);
}
