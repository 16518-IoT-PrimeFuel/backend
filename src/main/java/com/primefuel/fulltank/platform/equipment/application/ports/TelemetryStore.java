package com.primefuel.fulltank.platform.equipment.application.ports;

import java.time.Instant;

public interface TelemetryStore {
    boolean saveIfAbsent(TelemetryReadingData reading);
}
