package com.primefuel.fulltank.platform.equipment.application.internal.commandservices;

import com.primefuel.fulltank.platform.equipment.application.commandservices.TankReadingService;
import com.primefuel.fulltank.platform.equipment.domain.model.aggregates.Tank;
import com.primefuel.fulltank.platform.equipment.domain.repositories.TankRepository;
import com.primefuel.fulltank.platform.shared.application.result.ApplicationError;
import com.primefuel.fulltank.platform.shared.application.result.Result;
import com.primefuel.fulltank.platform.shared.domain.model.valueobjects.Unit;
import com.primefuel.fulltank.platform.shared.domain.model.valueobjects.Volume;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class TankReadingServiceImpl implements TankReadingService {

    private final TankRepository tankRepository;

    public TankReadingServiceImpl(TankRepository tankRepository) {
        this.tankRepository = tankRepository;
    }

    @Override
    @Transactional
    public Result<Tank, ApplicationError> applyValidatedReading(Long tankId, Double level, String unit,
                                                               Instant observedAt) {
        var existing = tankRepository.findById(tankId);
        if (existing.isEmpty()) {
            return Result.failure(ApplicationError.notFound("Tank", String.valueOf(tankId)));
        }
        var tank = existing.get();
        Volume volume;
        try {
            volume = Volume.of(level, Unit.fromCode(unit));
            if (!tank.applyValidatedReading(volume, observedAt)) {
                // Out-of-order reading: keep the newer snapshot untouched.
                return Result.success(tank);
            }
        } catch (IllegalArgumentException exception) {
            return Result.failure(ApplicationError.validationError("level", exception.getMessage()));
        }
        return Result.success(tankRepository.save(tank));
    }

    @Override
    @Transactional
    public Result<Tank, ApplicationError> applyManualLevel(Long tankId, Double level, String unit) {
        var existing = tankRepository.findById(tankId);
        if (existing.isEmpty()) {
            return Result.failure(ApplicationError.notFound("Tank", String.valueOf(tankId)));
        }
        var tank = existing.get();
        try {
            tank.updateLevelManually(Volume.of(level, Unit.fromCode(unit)));
        } catch (IllegalArgumentException exception) {
            return Result.failure(ApplicationError.validationError("level", exception.getMessage()));
        }
        return Result.success(tankRepository.save(tank));
    }
}
