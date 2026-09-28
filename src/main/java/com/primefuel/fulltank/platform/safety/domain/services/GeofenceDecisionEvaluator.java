package com.primefuel.fulltank.platform.safety.domain.services;

import com.primefuel.fulltank.platform.safety.domain.model.aggregates.GeofencePolicy;
import com.primefuel.fulltank.platform.safety.domain.model.valueobjects.GeofenceBlockReason;
import com.primefuel.fulltank.platform.safety.domain.model.valueobjects.GeofenceDecision;
import com.primefuel.fulltank.platform.safety.domain.model.valueobjects.TrackedPosition;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * The pure, versioned geofence decision (S17/U09). Given a {@link GeofencePolicy}, a
 * {@link TrackedPosition} and the evaluation instant, it returns {@link GeofenceDecision.Authorized} or
 * {@link GeofenceDecision.Blocked}.
 *
 * <p>Absolute time is never read here (the {@code now} is passed in), so the decision is deterministic and
 * fully testable. The rules, in order (U09):
 * <ol>
 *   <li><strong>Freshness</strong> — a position older than {@link #MAX_POSITION_AGE} is stale → blocked, and
 *       one dated beyond {@link #MAX_CLOCK_SKEW} in the future is untrustworthy → blocked;</li>
 *   <li><strong>Accuracy</strong> — an unknown or worse-than-{@link #MAX_ACCURACY_METERS} accuracy is
 *       unreliable → blocked (incertidumbre deniega);</li>
 *   <li><strong>Distance</strong> — the uncertainty circle must be <em>strictly</em> inside the radius. A
 *       border is denied: {@code distance + accuracy >= radius} blocks, so only
 *       {@code distance + accuracy < radius} authorizes.</li>
 * </ol>
 *
 * <p>The outcome is a decision, not a physical command: naming the events {@code ValveAuthorized} /
 * {@code ValveBlocked} mirrors the roadmap, but nothing here opens a valve (T18).
 */
@Component("geofenceDecisionEvaluator")
public class GeofenceDecisionEvaluator {

    public static final Duration MAX_POSITION_AGE = Duration.ofMinutes(5);
    public static final double MAX_ACCURACY_METERS = 50.0;
    /** Same tolerance the evidence recorder applies when it rejects a future-dated sample. */
    public static final Duration MAX_CLOCK_SKEW = Duration.ofMinutes(2);

    public GeofenceDecision evaluate(GeofencePolicy policy, TrackedPosition position, Instant now) {
        double distance = GeoDistance.metersBetween(position.latitude(), position.longitude(),
                policy.getCenterLatitude(), policy.getCenterLongitude());
        Double accuracy = position.accuracyMeters();

        if (position.recordedAt().isAfter(now.plus(MAX_CLOCK_SKEW))) {
            return new GeofenceDecision.Blocked(GeofenceBlockReason.FUTURE_TIMESTAMP, distance, accuracy);
        }
        if (statusIsStale(position.recordedAt(), now)) {
            return new GeofenceDecision.Blocked(GeofenceBlockReason.STALE, distance, accuracy);
        }
        if (accuracy == null || accuracy > MAX_ACCURACY_METERS) {
            return new GeofenceDecision.Blocked(GeofenceBlockReason.INACCURATE, distance, accuracy);
        }
        if (distance + accuracy >= policy.getRadiusMeters()) {
            return new GeofenceDecision.Blocked(GeofenceBlockReason.OUTSIDE, distance, accuracy);
        }
        return new GeofenceDecision.Authorized(distance, accuracy);
    }

    /** A position strictly older than {@link #MAX_POSITION_AGE} is stale (exactly on the window is fresh). */
    public boolean statusIsStale(Instant recordedAt, Instant now) {
        return recordedAt.isBefore(now.minus(MAX_POSITION_AGE));
    }
}
