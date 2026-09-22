package com.primefuel.fulltank.platform.telemetry.domain.model.aggregates;

import com.primefuel.fulltank.platform.shared.domain.model.aggregates.AbstractDomainAggregateRoot;
import com.primefuel.fulltank.platform.shared.domain.model.valueobjects.Volume;
import com.primefuel.fulltank.platform.telemetry.domain.model.valueobjects.ReadingQuality;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Normalised, append-only telemetry reading. It records what the device sent ({@code capturedAt}) and
 * when the platform received it ({@code receivedAt}) separately, and keeps quarantine outcomes instead of
 * discarding them, so ingestion is observable.
 */
@Getter
@Setter
@NoArgsConstructor
public class TelemetryReading extends AbstractDomainAggregateRoot<TelemetryReading> {

    private Long id;
    private int schemaVersion;
    private String deviceId;
    private String channel;
    private long sequence;
    private Instant capturedAt;
    private Instant receivedAt;
    private Long tankId;
    private Long organizationId;
    private Volume level;
    private ReadingQuality quality;
    private String quarantineReason;

    public TelemetryReading(int schemaVersion, String deviceId, String channel, long sequence,
                            Instant capturedAt, Instant receivedAt, Volume level,
                            Long tankId, Long organizationId, ReadingQuality quality, String quarantineReason) {
        if (schemaVersion <= 0) {
            throw new IllegalArgumentException("A schema version is required");
        }
        if (deviceId == null || deviceId.isBlank()) {
            throw new IllegalArgumentException("A device id is required");
        }
        if (channel == null || channel.isBlank()) {
            throw new IllegalArgumentException("A device channel is required");
        }
        if (capturedAt == null) {
            throw new IllegalArgumentException("A capture instant is required");
        }
        if (receivedAt == null) {
            throw new IllegalArgumentException("A reception instant is required");
        }
        if (level == null) {
            throw new IllegalArgumentException("A level is required");
        }
        this.schemaVersion = schemaVersion;
        this.deviceId = deviceId;
        this.channel = channel;
        this.sequence = sequence;
        this.capturedAt = capturedAt;
        this.receivedAt = receivedAt;
        this.level = level;
        this.tankId = tankId;
        this.organizationId = organizationId;
        this.quality = quality == null ? ReadingQuality.QUARANTINED : quality;
        this.quarantineReason = quarantineReason;
    }

    public boolean isAccepted() {
        return quality == ReadingQuality.ACCEPTED;
    }
}
