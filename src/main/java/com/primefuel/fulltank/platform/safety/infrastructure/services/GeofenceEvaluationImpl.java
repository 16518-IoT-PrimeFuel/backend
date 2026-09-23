package com.primefuel.fulltank.platform.safety.infrastructure.services;

import com.primefuel.fulltank.platform.fulfillment.api.DeliveryTrackingLookup;
import com.primefuel.fulltank.platform.safety.api.GeofenceEvaluation;
import com.primefuel.fulltank.platform.safety.api.events.ValveAuthorized;
import com.primefuel.fulltank.platform.safety.api.events.ValveBlocked;
import com.primefuel.fulltank.platform.safety.domain.model.entities.SafetyDecision;
import com.primefuel.fulltank.platform.safety.domain.model.valueobjects.GeofenceBlockReason;
import com.primefuel.fulltank.platform.safety.domain.model.valueobjects.GeofenceDecision;
import com.primefuel.fulltank.platform.safety.domain.model.valueobjects.TrackedPosition;
import com.primefuel.fulltank.platform.safety.domain.repositories.GeofencePolicyRepository;
import com.primefuel.fulltank.platform.safety.domain.repositories.SafetyDecisionRepository;
import com.primefuel.fulltank.platform.safety.domain.services.GeofenceDecisionEvaluator;
import com.primefuel.fulltank.platform.shared.application.result.ApplicationError;
import com.primefuel.fulltank.platform.shared.application.result.Result;
import com.primefuel.fulltank.platform.shared.events.EventPublicationRegistry;
import com.primefuel.fulltank.platform.tracking.api.DeliveryTrackingQuery;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * Evaluates the delivery's latest trusted position against the geofence policy in force, records the
 * decision (append-only) and publishes the {@code ValveAuthorized}/{@code ValveBlocked} event (S17).
 *
 * <p>It resolves both inputs through public seams — the policy from {@code safety}'s own repository, the
 * position from {@code tracking.api.DeliveryTrackingQuery.latest} — and reads no absolute time directly:
 * the {@link Clock} is injected, so freshness is deterministic under test.
 *
 * <p>Fail-closed: a missing policy and a missing position are each a blocked decision ({@code NO_POLICY} /
 * {@code NO_POSITION}); the only failure is a delivery that does not exist. The decision is persisted and
 * the event is published in one transaction, so a rollback leaves neither.
 */
@Component("geofenceEvaluation")
public class GeofenceEvaluationImpl implements GeofenceEvaluation {

    private static final String AGGREGATE_TYPE = "SafetyDecision";
    static final String AUTHORIZED_EVENT_TYPE = "safety.valve.authorized.v1";
    static final String BLOCKED_EVENT_TYPE = "safety.valve.blocked.v1";
    /** A decision is written once and never updated, so its aggregate version is always 1. */
    private static final long DECISION_AGGREGATE_VERSION = 1L;

    private final GeofencePolicyRepository policyRepository;
    private final SafetyDecisionRepository decisionRepository;
    private final DeliveryTrackingLookup deliveryLookup;
    private final DeliveryTrackingQuery trackingQuery;
    private final GeofenceDecisionEvaluator evaluator;
    private final EventPublicationRegistry publicationRegistry;
    private final Clock clock;

    public GeofenceEvaluationImpl(GeofencePolicyRepository policyRepository,
                                  SafetyDecisionRepository decisionRepository,
                                  DeliveryTrackingLookup deliveryLookup,
                                  DeliveryTrackingQuery trackingQuery,
                                  GeofenceDecisionEvaluator evaluator,
                                  EventPublicationRegistry publicationRegistry,
                                  Clock clock) {
        this.policyRepository = policyRepository;
        this.decisionRepository = decisionRepository;
        this.deliveryLookup = deliveryLookup;
        this.trackingQuery = trackingQuery;
        this.evaluator = evaluator;
        this.publicationRegistry = publicationRegistry;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Result<DecisionSnapshot, ApplicationError> evaluate(Long deliveryId) {
        if (deliveryId == null) {
            return Result.failure(ApplicationError.validationError("deliveryId", "A delivery is required"));
        }
        var now = clock.instant();
        var policy = policyRepository.findLatestByDeliveryId(deliveryId);

        SafetyDecision decision;
        if (policy.isEmpty()) {
            var delivery = deliveryLookup.findAssignedDelivery(deliveryId);
            if (delivery.isEmpty()) {
                return Result.failure(ApplicationError.notFound("Delivery", String.valueOf(deliveryId)));
            }
            decision = SafetyDecision.recordWithoutPolicy(deliveryId, delivery.get().providerId(), now);
        } else {
            var geofence = policy.get();
            var position = trackingQuery.latest(deliveryId)
                    .map(GeofenceEvaluationImpl::toTrackedPosition)
                    .orElse(null);
            GeofenceDecision outcome = position == null
                    ? new GeofenceDecision.Blocked(GeofenceBlockReason.NO_POSITION, null, null)
                    : evaluator.evaluate(geofence, position, now);
            decision = SafetyDecision.record(geofence, outcome, position, now);
        }

        var saved = decisionRepository.save(decision);

        publicationRegistry.publish(
                saved.isAuthorized() ? AUTHORIZED_EVENT_TYPE : BLOCKED_EVENT_TYPE,
                AGGREGATE_TYPE, String.valueOf(saved.getId()), saved.getProviderId(),
                DECISION_AGGREGATE_VERSION, payload(saved));

        return Result.success(toSnapshot(saved));
    }

    private static TrackedPosition toTrackedPosition(DeliveryTrackingQuery.TrackingSnapshot snapshot) {
        if (snapshot.lastPositionAt() == null || snapshot.lastLatitude() == null
                || snapshot.lastLongitude() == null) {
            return null;
        }
        return new TrackedPosition(snapshot.lastLatitude(), snapshot.lastLongitude(),
                snapshot.lastAccuracyMeters(), snapshot.lastPositionAt(), snapshot.lastPositionEvidenceId());
    }

    private static String payload(SafetyDecision saved) {
        if (saved.isAuthorized()) {
            return new ValveAuthorized(saved.getId(), saved.getDeliveryId(), saved.getPolicyId(),
                    saved.getPolicyVersion(), saved.getTrackingEvidenceId(), saved.getDistanceMeters(),
                    saved.getAccuracyMeters(), saved.getObservedAt(), saved.getEvaluatedAt()).toPayloadJson();
        }
        return new ValveBlocked(saved.getId(), saved.getDeliveryId(), saved.getPolicyId(),
                saved.getPolicyVersion(), saved.getReason(), saved.getTrackingEvidenceId(),
                saved.getDistanceMeters(), saved.getAccuracyMeters(), saved.getObservedAt(),
                saved.getEvaluatedAt()).toPayloadJson();
    }

    private static DecisionSnapshot toSnapshot(SafetyDecision decision) {
        return new DecisionSnapshot(decision.getId(), decision.getDeliveryId(), decision.getPolicyId(),
                decision.getPolicyVersion(), decision.isAuthorized(), decision.getReason(),
                decision.getTrackingEvidenceId(), decision.getObservedAt(), decision.getEvaluatedAt(),
                decision.getDistanceMeters(), decision.getAccuracyMeters());
    }
}
