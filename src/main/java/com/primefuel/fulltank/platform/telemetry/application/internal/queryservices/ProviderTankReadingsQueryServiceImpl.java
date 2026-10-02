package com.primefuel.fulltank.platform.telemetry.application.internal.queryservices;

import com.primefuel.fulltank.platform.equipment.api.ProviderBuyerAccess;
import com.primefuel.fulltank.platform.equipment.api.TankAssets;
import com.primefuel.fulltank.platform.shared.application.result.*;
import com.primefuel.fulltank.platform.telemetry.application.queryservices.ProviderTankReadingsQueryService;
import com.primefuel.fulltank.platform.telemetry.domain.model.aggregates.TelemetryReading;
import com.primefuel.fulltank.platform.telemetry.domain.model.queries.GetProviderTankReadingsQuery;
import com.primefuel.fulltank.platform.telemetry.domain.repositories.TelemetryReadingRepository;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProviderTankReadingsQueryServiceImpl implements ProviderTankReadingsQueryService {
    private final ProviderBuyerAccess buyers;
    private final TankAssets tanks;
    private final TelemetryReadingRepository readings;

    public ProviderTankReadingsQueryServiceImpl(
            ProviderBuyerAccess buyers, TankAssets tanks, TelemetryReadingRepository readings) {
        this.buyers = buyers;
        this.tanks = tanks;
        this.readings = readings;
    }

    public Result<List<TelemetryReading>, ApplicationError> handle(GetProviderTankReadingsQuery q) {
        if (!buyers.canReadTank(q.providerId(), q.tankId()))
            return Result.failure(ApplicationError.notFound("Tank", String.valueOf(q.tankId())));
        var tank = tanks.findById(q.tankId()).orElse(null);
        if (tank == null)
            return Result.failure(ApplicationError.notFound("Tank", String.valueOf(q.tankId())));
        if (q.from() != null && q.to() != null && q.from().isAfter(q.to()))
            return Result.failure(
                    ApplicationError.validationError("period", "from must not follow to"));
        return Result.success(
                readings
                        .findAcceptedByTankIdAndOrganizationId(q.tankId(), tank.organizationId())
                        .stream()
                        .filter(r -> q.from() == null || !r.getCapturedAt().isBefore(q.from()))
                        .filter(r -> q.to() == null || !r.getCapturedAt().isAfter(q.to()))
                        .toList());
    }
}
