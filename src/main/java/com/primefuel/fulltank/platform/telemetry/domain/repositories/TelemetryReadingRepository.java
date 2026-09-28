package com.primefuel.fulltank.platform.telemetry.domain.repositories;

import com.primefuel.fulltank.platform.telemetry.domain.model.aggregates.TelemetryReading;
import com.primefuel.fulltank.platform.telemetry.domain.model.valueobjects.ReadingQuality;

import java.util.List;
import java.util.Optional;

public interface TelemetryReadingRepository {
    Optional<TelemetryReading> findByDeviceChannelAndSequence(String deviceId, String channel, long sequence);
    List<TelemetryReading> findByDeviceAndChannelOrderByCapturedAtAsc(String deviceId, String channel);
    long countByQuality(ReadingQuality quality);
    TelemetryReading save(TelemetryReading reading);
}
