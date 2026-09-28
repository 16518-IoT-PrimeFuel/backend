package com.primefuel.fulltank.platform.replenishment.infrastructure.persistence.jpa.repositories;

import com.primefuel.fulltank.platform.replenishment.infrastructure.persistence.jpa.entities.RefillPolicyPersistenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RefillPolicyPersistenceRepository extends JpaRepository<RefillPolicyPersistenceEntity, Long> {

    Optional<RefillPolicyPersistenceEntity> findByTankId(Long tankId);
}
