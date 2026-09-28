package com.primefuel.fulltank.platform.safety.api.events;

import java.time.Instant;

/**
 * A geofence decision blocked the (future) valve command (S17). Event type {@code safety.valve.blocked.v1}.
 *
 * <p>Name and shape follow the roadmap's {@code ValveBlocked} domain event; {@code reason} carries the
 * {@code GeofenceBlockReason} that produced the block (stale, inaccurate, outside/border, no position, no
 * policy). {@code policyId}/{@code policyVersion} are {@code null} for a no-policy block.
 */
public record ValveBlocked(
        Long decisionId,
        Long deliveryId,
        Long policyId,
        Integer policyVersion,
        String reason,
        Long trackingEvidenceId,
        Double distanceMeters,
        Double accuracyMeters,
        Instant observedAt,
        Instant evaluatedAt) {

    public String toPayloadJson() {
        return "{\"decisionId\":%s,\"deliveryId\":%s,\"policyId\":%s,\"policyVersion\":%s,\"reason\":%s,"
                .formatted(SafetyEventJson.number(decisionId), SafetyEventJson.number(deliveryId),
                        SafetyEventJson.number(policyId), policyVersion, SafetyEventJson.string(reason))
                + "\"trackingEvidenceId\":%s,\"distanceMeters\":%s,\"accuracyMeters\":%s,"
                .formatted(SafetyEventJson.number(trackingEvidenceId), SafetyEventJson.number(distanceMeters),
                        SafetyEventJson.number(accuracyMeters))
                + "\"observedAt\":%s,\"evaluatedAt\":%s}".formatted(SafetyEventJson.string(observedAt),
                        SafetyEventJson.string(evaluatedAt));
    }
}
