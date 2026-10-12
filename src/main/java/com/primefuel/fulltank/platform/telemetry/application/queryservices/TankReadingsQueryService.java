package com.primefuel.fulltank.platform.telemetry.application.queryservices;

import com.primefuel.fulltank.platform.shared.application.result.*;
import com.primefuel.fulltank.platform.telemetry.domain.model.aggregates.TelemetryReading;
import com.primefuel.fulltank.platform.telemetry.domain.model.queries.GetTankReadingsQuery;

import java.util.List;

public interface TankReadingsQueryService {
    Result<List<TelemetryReading>, ApplicationError> handle(GetTankReadingsQuery query);
}
