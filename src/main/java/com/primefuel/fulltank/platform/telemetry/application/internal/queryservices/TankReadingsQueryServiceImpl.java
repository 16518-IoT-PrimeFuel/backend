package com.primefuel.fulltank.platform.telemetry.application.internal.queryservices;

import com.primefuel.fulltank.platform.equipment.api.TankAssets;
import com.primefuel.fulltank.platform.shared.application.result.*;
import com.primefuel.fulltank.platform.telemetry.application.queryservices.TankReadingsQueryService;
import com.primefuel.fulltank.platform.telemetry.domain.model.aggregates.TelemetryReading;
import com.primefuel.fulltank.platform.telemetry.domain.model.queries.GetTankReadingsQuery;
import com.primefuel.fulltank.platform.telemetry.domain.repositories.TelemetryReadingRepository;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TankReadingsQueryServiceImpl implements TankReadingsQueryService {
    private final TankAssets tanks;
    private final TelemetryReadingRepository readings;

    public TankReadingsQueryServiceImpl(TankAssets tanks, TelemetryReadingRepository readings) {
        this.tanks = tanks;
        this.readings = readings;
    }

    /** Un tanque de otra organización responde como inexistente, igual que GET /api/tanks/{id}. */
    public Result<List<TelemetryReading>, ApplicationError> handle(GetTankReadingsQuery q) {
        var tank = tanks.findById(q.tankId()).orElse(null);
        if (tank == null || !q.organizationId().equals(tank.organizationId()))
            return Result.failure(ApplicationError.notFound("Tank", String.valueOf(q.tankId())));
        if (q.from() != null && q.to() != null && q.from().isAfter(q.to()))
            return Result.failure(
                    ApplicationError.validationError("period", "from must not follow to"));
        return Result.success(
                readings.findAcceptedByTankIdAndOrganizationId(q.tankId(), q.organizationId())
                        .stream()
                        .filter(r -> q.from() == null || !r.getCapturedAt().isBefore(q.from()))
                        .filter(r -> q.to() == null || !r.getCapturedAt().isAfter(q.to()))
                        .toList());
    }
}
