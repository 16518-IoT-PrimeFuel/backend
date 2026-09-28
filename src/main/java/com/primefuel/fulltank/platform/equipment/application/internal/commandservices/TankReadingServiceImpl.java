package com.primefuel.fulltank.platform.equipment.application.internal.commandservices;

import com.primefuel.fulltank.platform.equipment.api.events.TankLevelManuallyUpdatedEvent;
import com.primefuel.fulltank.platform.equipment.domain.model.aggregates.Tank;
import com.primefuel.fulltank.platform.equipment.domain.repositories.TankRepository;
import com.primefuel.fulltank.platform.shared.application.result.ApplicationError;
import com.primefuel.fulltank.platform.shared.application.result.Result;
import com.primefuel.fulltank.platform.shared.domain.model.valueobjects.Unit;
import com.primefuel.fulltank.platform.shared.domain.model.valueobjects.Volume;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class TankReadingServiceImpl {

    private final TankRepository tankRepository;
    private final ApplicationEventPublisher events;

    public TankReadingServiceImpl(TankRepository tankRepository, ApplicationEventPublisher events) {
        this.tankRepository = tankRepository;
        this.events = events;
    }
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
        var saved = tankRepository.save(tank);
        // A manual edit is a first-class level change: publish it so the refill policy (S09) evaluates it
        // exactly like a telemetry reading, without depending on the frozen IoT telemetry infrastructure.
        var observedAt = saved.getLevelObservedAt();
        events.publishEvent(new TankLevelManuallyUpdatedEvent(
                saved.getId(), saved.getOrganizationId(), observedAt,
                "manual:" + observedAt.toEpochMilli()));
        return Result.success(saved);
    }
}
