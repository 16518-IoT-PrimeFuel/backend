package com.primefuel.fulltank.fog.domain;

import com.primefuel.fulltank.fog.config.FogProperties;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

/**
 * Reglas que la Fog aplica antes de reenviar. Filtra lecturas imposibles o de dispositivos no esperados; las reglas
 * de negocio (umbral crítico, reposición) siguen en el backend.
 */
@Component
public class ReadingPolicy {

    static final int SUPPORTED_SCHEMA_VERSION = 1;

    private final FogProperties.Policy policy;
    private final Clock clock;

    public ReadingPolicy(FogProperties properties, Clock clock) {
        this.policy = properties.policy();
        this.clock = clock;
    }

    /** Devuelve el motivo del rechazo, o vacío si la lectura puede reenviarse. */
    public Optional<String> violation(Reading reading) {
        if (reading.schemaVersion() != SUPPORTED_SCHEMA_VERSION) {
            return Optional.of("schemaVersion no admitida: " + reading.schemaVersion());
        }
        if (!policy.allowedDeviceIds().isEmpty() && !policy.allowedDeviceIds().contains(reading.deviceId())) {
            return Optional.of("deviceId no autorizado en esta Fog: " + reading.deviceId());
        }
        if (reading.sequence() < 0) {
            return Optional.of("sequence negativa");
        }
        double level = reading.level();
        if (Double.isNaN(level) || level < policy.minLevel() || level > policy.maxLevel()) {
            return Optional.of("level fuera de rango [" + policy.minLevel() + ", " + policy.maxLevel() + "]: " + level);
        }
        Instant limit = clock.instant().plus(policy.maxClockSkew());
        if (reading.capturedAt().isAfter(limit)) {
            return Optional.of("capturedAt en el futuro (¿NTP sin sincronizar?): " + reading.capturedAt());
        }
        return Optional.empty();
    }
}
