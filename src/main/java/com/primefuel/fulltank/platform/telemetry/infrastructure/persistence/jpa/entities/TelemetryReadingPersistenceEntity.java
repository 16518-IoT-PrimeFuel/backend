package com.primefuel.fulltank.platform.telemetry.infrastructure.persistence.jpa.entities;

import com.primefuel.fulltank.platform.shared.infrastructure.persistence.jpa.entities.AuditableAbstractPersistenceEntity;
import com.primefuel.fulltank.platform.telemetry.domain.model.valueobjects.ReadingQuality;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(
        name = "telemetry_readings",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_telemetry_readings_device_sequence",
                columnNames = {"device_id", "channel", "sequence_number"}))
@Getter
@Setter
@NoArgsConstructor
public class TelemetryReadingPersistenceEntity extends AuditableAbstractPersistenceEntity {

    @Column(name = "schema_version", nullable = false)
    private int schemaVersion;

    @Column(name = "device_id", nullable = false, length = 120)
    private String deviceId;

    @Column(nullable = false, length = 60)
    private String channel;

    @Column(name = "sequence_number", nullable = false)
    private long sequence;

    @Column(name = "captured_at", nullable = false)
    private Instant capturedAt;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "tank_id")
    private Long tankId;

    @Column(name = "organization_id")
    private Long organizationId;

    @Column(name = "level_amount", nullable = false)
    private double levelAmount;

    @Column(name = "level_unit", nullable = false, length = 20)
    private String levelUnit;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20, columnDefinition = "VARCHAR(20)")
    private ReadingQuality quality;

    @Column(name = "quarantine_reason", length = 80)
    private String quarantineReason;
}
