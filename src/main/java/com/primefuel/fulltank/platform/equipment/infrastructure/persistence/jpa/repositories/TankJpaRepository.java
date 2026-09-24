package com.primefuel.fulltank.platform.equipment.infrastructure.persistence.jpa.repositories;

import com.primefuel.fulltank.platform.equipment.infrastructure.persistence.jpa.entities.TankPersistenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TankJpaRepository extends JpaRepository<TankPersistenceEntity, Long> {
}
