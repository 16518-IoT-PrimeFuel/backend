package com.primefuel.fulltank.platform.equipment.domain.repositories;

import com.primefuel.fulltank.platform.equipment.domain.model.aggregates.Tank;

import java.util.List;
import java.util.Optional;

public interface TankRepository {
    Optional<Tank> findById(Long id);
    List<Tank> findByOrganizationId(Long organizationId);
    Optional<Tank> findByLegacyEquipmentId(Long legacyEquipmentId);
    Tank save(Tank tank);
    boolean existsById(Long id);
}
