package com.primefuel.fulltank.platform.equipment.infrastructure.services;

import com.primefuel.fulltank.platform.equipment.api.TankAssets;
import com.primefuel.fulltank.platform.equipment.application.internal.commandservices.TankReadingServiceImpl;
import com.primefuel.fulltank.platform.equipment.domain.model.aggregates.Tank;
import com.primefuel.fulltank.platform.equipment.domain.repositories.TankRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;

@Component("tankAssets")
public class TankAssetsImpl implements TankAssets {

    private final TankRepository tankRepository;
    private final TankReadingServiceImpl tankReadingService;

    public TankAssetsImpl(TankRepository tankRepository, TankReadingServiceImpl tankReadingService) {
        this.tankRepository = tankRepository;
        this.tankReadingService = tankReadingService;
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

    @Override
    public boolean applyValidatedReading(Long tankId, double level, String unit, Instant observedAt) {
        var before = tankRepository.findById(tankId).orElse(null);
        if (before == null) {
            return false;
        }
        var result = tankReadingService.applyValidatedReading(tankId, level, unit, observedAt);
        if (result.isFailure()) {
            throw new IllegalArgumentException("The reading could not be applied to the tank");
        }
        // An out-of-order observation leaves the snapshot untouched, so the change signal is the instant.
        return !before.getLevelObservedAt().equals(result.getOrElse(before).getLevelObservedAt());
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
