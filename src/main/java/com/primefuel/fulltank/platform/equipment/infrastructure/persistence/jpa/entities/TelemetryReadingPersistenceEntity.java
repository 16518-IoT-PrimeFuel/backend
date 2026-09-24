package com.primefuel.fulltank.platform.equipment.infrastructure.persistence.jpa.entities;

import com.primefuel.fulltank.platform.shared.infrastructure.persistence.jpa.entities.AuditableAbstractPersistenceEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "telemetry_readings")
@Getter
@Setter
@NoArgsConstructor
public class TelemetryReadingPersistenceEntity extends AuditableAbstractPersistenceEntity {
    @Column(name = "event_id", nullable = false, unique = true, length = 160)
    private String eventId;
    @Column(name = "device_id", nullable = false, length = 160)
    private String deviceId;
    @Column(nullable = false, length = 80)
    private String channel;
    @Column(name = "sequence_number", nullable = false)
    private long sequenceNumber;
    @Column(name = "tank_id", nullable = false)
    private Long tankId;
    @Column(name = "schema_version", nullable = false, length = 40)
    private String schemaVersion;
    @Column(name = "captured_at", nullable = false)
    private Instant capturedAt;
    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;
    @Column(name = "level_value", nullable = false)
    private double levelValue;
    @Column(nullable = false, length = 20)
    private String unit;
    @Column(nullable = false, length = 20)
    private String quality;
}
