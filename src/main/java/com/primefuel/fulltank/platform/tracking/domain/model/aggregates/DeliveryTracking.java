package com.primefuel.fulltank.platform.tracking.domain.model.aggregates;

import com.primefuel.fulltank.platform.shared.domain.model.aggregates.AbstractDomainAggregateRoot;
import com.primefuel.fulltank.platform.shared.domain.model.valueobjects.Volume;
import com.primefuel.fulltank.platform.tracking.domain.model.valueobjects.GeoPosition;
import com.primefuel.fulltank.platform.tracking.domain.model.valueobjects.LoadMilestone;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * The tracking projection of a delivery (S16/T16-A): the <em>latest trusted</em> position and the latest
 * load milestone. Frequent samples stay out of the {@code Delivery} aggregate; this projection reduces their
 * interface to a single consultable row, while every raw sample is kept separately and referenced by id.
 *
 * <p>Its central invariant is the one inherited from S16: <strong>a late sample never regresses the
 * latest</strong>. {@link #recordPosition} advances the projection only when the observation is strictly
 * newer than the current one; a late (or tied) sample returns {@code false}, so the caller can persist it as
 * raw evidence without moving the latest. The load milestone guards that an {@code UNLOADED} cannot precede a
 * {@code LOADED}.
 */
@Getter
@Setter
@NoArgsConstructor
public class DeliveryTracking extends AbstractDomainAggregateRoot<DeliveryTracking> {

    private Long id;
    private Long deliveryId;
    private Long providerId;
    private Long driverId;

    private Double lastLatitude;
    private Double lastLongitude;
    private Double lastAccuracyMeters;
    private Instant lastPositionAt;
    private Long lastPositionEvidenceId;

    private boolean loaded;
    private LoadMilestone lastLoadMilestone;
    private Instant lastLoadAt;
    private Double lastLoadVolume;
    private String lastLoadUnit;
    private Long lastLoadEvidenceId;

    private int version;

    public DeliveryTracking(Long deliveryId, Long providerId, Long driverId) {
        if (deliveryId == null) {
            throw new IllegalArgumentException("A delivery is required");
        }
        if (providerId == null) {
            throw new IllegalArgumentException("A provider is required");
        }
        this.deliveryId = deliveryId;
        this.providerId = providerId;
        this.driverId = driverId;
    }

    /**
     * Records a position sample and reports whether it moved the latest trusted position. A sample whose
     * {@code recordedAt} is not strictly after the current {@link #getLastPositionAt()} is <em>late</em>: the
     * projection is left untouched ({@code false}) so the caller stores the sample as raw evidence only.
     */
    public boolean recordPosition(GeoPosition position, Instant recordedAt) {
        if (recordedAt == null) {
            throw new IllegalArgumentException("A position timestamp is required");
        }
        if (lastPositionAt != null && !recordedAt.isAfter(lastPositionAt)) {
            return false;
        }
        this.lastLatitude = position.latitude();
        this.lastLongitude = position.longitude();
        this.lastAccuracyMeters = position.accuracyMeters();
        this.lastPositionAt = recordedAt;
        return true;
    }

    /** Points the projection at the raw evidence row that produced the latest trusted position. */
    public void linkPositionEvidence(Long evidenceId) {
        this.lastPositionEvidenceId = evidenceId;
    }

    /**
     * Records a discrete load milestone. {@code UNLOADED} is only valid after a {@code LOADED} (a truck
     * cannot discharge what was never loaded); the declared volume is optional and, when present, replaces
     * the last declared one.
     */
    public void recordLoad(LoadMilestone milestone, Volume volume, Instant recordedAt) {
        if (milestone == LoadMilestone.UNLOADED && !loaded) {
            throw new IllegalStateException("The truck cannot be unloaded before it was loaded");
        }
        this.lastLoadMilestone = milestone;
        this.loaded = milestone == LoadMilestone.LOADED;
        this.lastLoadAt = recordedAt;
        if (volume != null) {
            this.lastLoadVolume = volume.amount();
            this.lastLoadUnit = volume.unit().name();
        }
    }

    /** Points the projection at the raw evidence row that produced the latest load milestone. */
    public void linkLoadEvidence(Long evidenceId) {
        this.lastLoadEvidenceId = evidenceId;
    }
}
