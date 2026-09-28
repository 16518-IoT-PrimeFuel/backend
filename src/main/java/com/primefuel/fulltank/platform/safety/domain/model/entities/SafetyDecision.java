package com.primefuel.fulltank.platform.safety.domain.model.entities;

import com.primefuel.fulltank.platform.safety.domain.model.aggregates.GeofencePolicy;
import com.primefuel.fulltank.platform.safety.domain.model.valueobjects.GeofenceBlockReason;
import com.primefuel.fulltank.platform.safety.domain.model.valueobjects.GeofenceDecision;
import com.primefuel.fulltank.platform.safety.domain.model.valueobjects.TrackedPosition;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * An append-only record of a geofence decision (S17): what was decided, under which policy version, and the
 * evidence that produced it. Rows are written once and never updated or deleted — they are the explanation
 * of a (future) valve outcome, not a mutable state.
 *
 * <p>It stores the evidence reference ({@link #trackingEvidenceId}) rather than the raw position so the
 * decision points back at the tracking sample, and the {@code policyVersion} it was taken under so a later
 * policy change never rewrites history.
 */
@Getter
@Setter
@NoArgsConstructor
public class SafetyDecision {

    private Long id;
    private Long deliveryId;
    private Long providerId;
    /** {@code null} only for a {@code NO_POLICY} block: the delivery had no policy to reference. */
    private Long policyId;
    private Integer policyVersion;
    private boolean authorized;
    private String reason;
    private Long trackingEvidenceId;
    private Instant observedAt;
    private Instant evaluatedAt;
    private Double distanceMeters;
    private Double accuracyMeters;

    /** Records the block taken because the delivery has no geofence policy: uncertainty denies. */
    public static SafetyDecision recordWithoutPolicy(Long deliveryId, Long providerId, Instant evaluatedAt) {
        var record = new SafetyDecision();
        record.deliveryId = deliveryId;
        record.providerId = providerId;
        record.authorized = false;
        record.reason = GeofenceBlockReason.NO_POLICY.name();
        record.evaluatedAt = evaluatedAt;
        return record;
    }

    /**
     * Materialises the decision against its policy and evidence. {@code position} is {@code null} only when
     * there was no trusted position (then the decision is blocked with {@code NO_POSITION} and no evidence).
     */
    public static SafetyDecision record(GeofencePolicy policy,
                                        GeofenceDecision decision,
                                        TrackedPosition position,
                                        Instant evaluatedAt) {
        var record = new SafetyDecision();
        record.deliveryId = policy.getDeliveryId();
        record.providerId = policy.getProviderId();
        record.policyId = policy.getId();
        record.policyVersion = policy.getPolicyVersion();
        record.authorized = decision.authorized();
        record.reason = decision.authorized() ? null : decision.reason().name();
        record.trackingEvidenceId = position == null ? null : position.evidenceId();
        record.observedAt = position == null ? null : position.recordedAt();
        record.evaluatedAt = evaluatedAt;
        record.distanceMeters = decision.distanceMeters();
        record.accuracyMeters = decision.accuracyMeters();
        return record;
    }
}
