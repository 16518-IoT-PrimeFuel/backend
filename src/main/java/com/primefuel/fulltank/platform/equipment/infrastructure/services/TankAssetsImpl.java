package com.primefuel.fulltank.platform.equipment.infrastructure.services;

import com.primefuel.fulltank.platform.equipment.api.TankAssets;
import com.primefuel.fulltank.platform.equipment.domain.model.aggregates.Tank;
import com.primefuel.fulltank.platform.equipment.domain.repositories.TankRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component("tankAssets")
public class TankAssetsImpl implements TankAssets {

    private final TankRepository tankRepository;

    public TankAssetsImpl(TankRepository tankRepository) {
        this.tankRepository = tankRepository;
    }

    @Override
    public Optional<TankSnapshot> findById(Long tankId) {
        return tankRepository.findById(tankId).map(this::toSnapshot);
    }

    @Override
    public Optional<Long> tankIdForLegacyEquipment(Long equipmentId) {
        if (equipmentId == null) {
            return Optional.empty();
        }
        return tankRepository.findByLegacyEquipmentId(equipmentId).map(Tank::getId);
    }

    private TankSnapshot toSnapshot(Tank tank) {
        return new TankSnapshot(
                tank.getId(),
                tank.getOrganizationId(),
                tank.getCustomerAccountId(),
                tank.getSiteId(),
                tank.getFuelType(),
                tank.getCapacity().unit().name(),
                tank.getCapacity().amount(),
                tank.getCurrentLevel().amount(),
                tank.getConfigurationVersion(),
                tank.isActive());
    }
}
