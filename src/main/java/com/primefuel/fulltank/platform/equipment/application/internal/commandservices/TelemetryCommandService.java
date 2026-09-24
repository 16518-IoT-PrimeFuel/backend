package com.primefuel.fulltank.platform.equipment.application.internal.commandservices;

import com.primefuel.fulltank.platform.equipment.application.ports.TankStore;
import com.primefuel.fulltank.platform.equipment.application.ports.TelemetryReadingData;
import com.primefuel.fulltank.platform.equipment.application.ports.TelemetryStore;
import com.primefuel.fulltank.platform.shared.application.events.DurableEvent;
import com.primefuel.fulltank.platform.shared.application.events.DurableEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class TelemetryCommandService {
    private final DeviceBindingCommandService bindings;
    private final TelemetryStore readings;
    private final TankStore tanks;
    private final DurableEventPublisher events;
    private final RefillPolicyCommandService refillPolicies;

    public TelemetryCommandService(DeviceBindingCommandService bindings, TelemetryStore readings,
                                   TankStore tanks, DurableEventPublisher events, RefillPolicyCommandService refillPolicies) {
        this.bindings = bindings;
        this.readings = readings;
        this.tanks = tanks;
        this.events = events;
        this.refillPolicies = refillPolicies;
    }

    @Transactional
    public boolean ingest(String deviceId, String channel, String credential, long sequenceNumber,
                          String eventId, String schemaVersion, Instant capturedAt, double levelValue,
                          String unit, String quality) {
        if (sequenceNumber < 0 || eventId == null || eventId.isBlank() || schemaVersion == null
                || schemaVersion.isBlank() || !Double.isFinite(levelValue) || levelValue < 0
                || unit == null || unit.isBlank() || quality == null || quality.isBlank()) {
            throw new IllegalArgumentException("Invalid telemetry payload");
        }
        var receivedAt = Instant.now();
        var binding = bindings.resolve(deviceId, channel, credential, capturedAt);
        var reading = new TelemetryReadingData(eventId, deviceId, channel, sequenceNumber, binding.tankId(),
                schemaVersion, capturedAt == null ? receivedAt : capturedAt, receivedAt, levelValue, unit, quality);
        if (!readings.saveIfAbsent(reading)) return false;
        tanks.applyValidatedReading(binding.tankId(), levelValue, reading.capturedAt());
        refillPolicies.evaluate(binding.tankId(), levelValue, quality, reading.capturedAt());
        events.publish(new DurableEvent("telemetry:" + eventId, "ValidatedTankReading", "Tank",
                binding.tankId().toString(), "eventId=" + eventId + ";deviceId=" + deviceId
                        + ";capturedAt=" + reading.capturedAt(), receivedAt));
        return true;
    }
}
