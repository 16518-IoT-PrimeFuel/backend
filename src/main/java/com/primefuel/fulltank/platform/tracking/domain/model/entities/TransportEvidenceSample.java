package com.primefuel.fulltank.platform.tracking.domain.model.entities;

import com.primefuel.fulltank.platform.shared.domain.model.valueobjects.Volume;
import com.primefuel.fulltank.platform.tracking.domain.model.valueobjects.GeoPosition;
import com.primefuel.fulltank.platform.tracking.domain.model.valueobjects.LoadMilestone;
import com.primefuel.fulltank.platform.tracking.domain.model.valueobjects.TransportEvidenceKind;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * A raw, append-only transport evidence sample (S16/T16-A). It keeps what the driver app sent
 * ({@code recordedAt}) and when the platform received it ({@code receivedAt}) separately, so history is
 * never fabricated — including samples that did <em>not</em> move the projection
 * ({@code latestAdvanced=false}).
 *
 * <p>It is a referenced detail of the {@link com.primefuel.fulltank.platform.tracking.domain.model.aggregates.DeliveryTracking}
 * projection, not a replacement for it: the projection holds the latest trusted value, the sample keeps the
 * trail.
 */
@Getter
@Setter
@NoArgsConstructor
public class TransportEvidenceSample {

    private Long id;
    private Long deliveryId;
    private Long providerId;
    private Long driverId;
    private TransportEvidenceKind kind;

    private Double latitude;
    private Double longitude;
    private Double accuracyMeters;

    private String milestone;
    private Double volume;
    private String unit;

    private Instant recordedAt;
    private Instant receivedAt;
    private boolean latestAdvanced;
    private String clientEventId;

    private TransportEvidenceSample(TransportEvidenceKind kind, Long deliveryId, Long providerId, Long driverId,
                                    Instant recordedAt, Instant receivedAt, boolean latestAdvanced) {
        if (deliveryId == null) {
            throw new IllegalArgumentException("A delivery is required");
        }
        if (providerId == null) {
            throw new IllegalArgumentException("A provider is required");
        }
        if (recordedAt == null) {
            throw new IllegalArgumentException("An observation instant is required");
        }
        if (receivedAt == null) {
            throw new IllegalArgumentException("A reception instant is required");
        }
        this.kind = kind;
        this.deliveryId = deliveryId;
        this.providerId = providerId;
        this.driverId = driverId;
        this.recordedAt = recordedAt;
        this.receivedAt = receivedAt;
        this.latestAdvanced = latestAdvanced;
    }

    public static TransportEvidenceSample position(Long deliveryId, Long providerId, Long driverId,
                                                   GeoPosition position, Instant recordedAt, Instant receivedAt,
                                                   boolean latestAdvanced) {
        var sample = new TransportEvidenceSample(TransportEvidenceKind.POSITION, deliveryId, providerId, driverId,
                recordedAt, receivedAt, latestAdvanced);
        sample.latitude = position.latitude();
        sample.longitude = position.longitude();
        sample.accuracyMeters = position.accuracyMeters();
        return sample;
    }

    public static TransportEvidenceSample load(Long deliveryId, Long providerId, Long driverId,
                                               LoadMilestone milestone, Volume volume, Instant recordedAt,
                                               Instant receivedAt, boolean latestAdvanced) {
        var sample = new TransportEvidenceSample(TransportEvidenceKind.LOAD, deliveryId, providerId, driverId,
                recordedAt, receivedAt, latestAdvanced);
        sample.milestone = milestone.name();
        if (volume != null) {
            sample.volume = volume.amount();
            sample.unit = volume.unit().name();
        }
        return sample;
    }
}
