package com.primefuel.fulltank.platform.equipment.application.commandservices;

import com.primefuel.fulltank.platform.equipment.domain.model.aggregates.Tank;
import com.primefuel.fulltank.platform.shared.application.result.ApplicationError;
import com.primefuel.fulltank.platform.shared.application.result.Result;

import java.time.Instant;

public interface TankReadingService {

    /**
     * Applies a telemetry-sourced reading. Out-of-order (older) observations are ignored so the
     * snapshot never regresses.
     */
    Result<Tank, ApplicationError> applyValidatedReading(Long tankId, Double level, String unit, Instant observedAt);

    /** Applies a manual level edit (legacy v1 path): metadata, not a measured reading. */
    Result<Tank, ApplicationError> applyManualLevel(Long tankId, Double level, String unit);
}
