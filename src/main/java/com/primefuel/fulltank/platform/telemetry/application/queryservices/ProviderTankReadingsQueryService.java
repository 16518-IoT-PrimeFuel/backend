package com.primefuel.fulltank.platform.telemetry.application.queryservices;

import com.primefuel.fulltank.platform.shared.application.result.*;
import com.primefuel.fulltank.platform.telemetry.domain.model.aggregates.TelemetryReading;
import com.primefuel.fulltank.platform.telemetry.domain.model.queries.GetProviderTankReadingsQuery;

import java.util.List;

public interface ProviderTankReadingsQueryService {
    Result<List<TelemetryReading>, ApplicationError> handle(GetProviderTankReadingsQuery query);
}
