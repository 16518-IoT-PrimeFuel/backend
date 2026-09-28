package com.primefuel.fulltank.platform.equipment.infrastructure.persistence.jpa.repositories;

import com.primefuel.fulltank.platform.equipment.infrastructure.persistence.jpa.entities.TankConfigurationPersistenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TankConfigurationPersistenceRepository
        extends JpaRepository<TankConfigurationPersistenceEntity, Long> {

    List<TankConfigurationPersistenceEntity> findByTankId(Long tankId);
}
