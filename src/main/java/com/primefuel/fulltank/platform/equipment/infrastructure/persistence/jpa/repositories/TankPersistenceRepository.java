package com.primefuel.fulltank.platform.equipment.infrastructure.persistence.jpa.repositories;

import com.primefuel.fulltank.platform.equipment.infrastructure.persistence.jpa.entities.TankPersistenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TankPersistenceRepository extends JpaRepository<TankPersistenceEntity, Long> {
    List<TankPersistenceEntity> findByOrganizationId(Long organizationId);
    Optional<TankPersistenceEntity> findByLegacyEquipmentId(Long legacyEquipmentId);
}
