package com.primefuel.fulltank.platform.safety.api;

import com.primefuel.fulltank.platform.shared.application.result.ApplicationError;
import com.primefuel.fulltank.platform.shared.application.result.Result;

import java.time.Instant;

/**
 * Public decision seam of the safety module (S17). It evaluates the delivery's latest trusted position
 * against the policy in force and records the outcome (append-only), publishing the {@code ValveAuthorized}
 * or {@code ValveBlocked} domain event.
 *
 * <p>This is a <strong>decision seam, not an actuator</strong>: it authorizes or blocks, it does not open a
 * valve. Wiring it into the delivery lifecycle is T17-B; the physical command/outbox/ACK is T18.
 */
public interface GeofenceEvaluation {

    /**
     * Evaluates and records the geofence decision for a delivery. Fails only when the delivery does not
     * exist. A delivery without a geofence policy, or without a trusted position, is itself a <em>blocked</em>
     * decision (reasons {@code NO_POLICY} / {@code NO_POSITION}), because uncertainty denies.
     */
    Result<DecisionSnapshot, ApplicationError> evaluate(Long deliveryId);

    record DecisionSnapshot(
            Long decisionId,
            Long deliveryId,
            Long policyId,
            Integer policyVersion,
            boolean authorized,
            String reason,
            Long trackingEvidenceId,
            Instant observedAt,
            Instant evaluatedAt,
            Double distanceMeters,
            Double accuracyMeters) {
    }
}
