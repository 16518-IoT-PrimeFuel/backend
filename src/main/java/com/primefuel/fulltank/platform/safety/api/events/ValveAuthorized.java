package com.primefuel.fulltank.platform.safety.api.events;

import java.time.Instant;

/**
 * A geofence decision authorized the (future) valve command (S17). Event type
 * {@code safety.valve.authorized.v1}.
 *
 * <p>Name and shape follow the roadmap's {@code ValveAuthorized} domain event. It is a <em>decision</em>
 * only: no physical command exists yet (T18).
 */
public record ValveAuthorized(
        Long decisionId,
        Long deliveryId,
        Long policyId,
        int policyVersion,
        Long trackingEvidenceId,
        Double distanceMeters,
        Double accuracyMeters,
        Instant observedAt,
        Instant evaluatedAt) {

    public String toPayloadJson() {
        return "{\"decisionId\":%s,\"deliveryId\":%s,\"policyId\":%s,\"policyVersion\":%s,"
                .formatted(SafetyEventJson.number(decisionId), SafetyEventJson.number(deliveryId),
                        SafetyEventJson.number(policyId), policyVersion)
                + "\"trackingEvidenceId\":%s,\"distanceMeters\":%s,\"accuracyMeters\":%s,"
                .formatted(SafetyEventJson.number(trackingEvidenceId), SafetyEventJson.number(distanceMeters),
                        SafetyEventJson.number(accuracyMeters))
                + "\"observedAt\":%s,\"evaluatedAt\":%s}".formatted(SafetyEventJson.string(observedAt),
                        SafetyEventJson.string(evaluatedAt));
    }
}
