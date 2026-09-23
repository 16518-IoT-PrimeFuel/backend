package com.primefuel.fulltank.platform.safety;

import com.primefuel.fulltank.platform.safety.domain.model.aggregates.GeofencePolicy;
import com.primefuel.fulltank.platform.safety.domain.model.commands.CreateGeofencePolicyCommand;
import com.primefuel.fulltank.platform.safety.domain.model.valueobjects.GeofenceBlockReason;
import com.primefuel.fulltank.platform.safety.domain.model.valueobjects.GeofenceDecision;
import com.primefuel.fulltank.platform.safety.domain.model.valueobjects.TrackedPosition;
import com.primefuel.fulltank.platform.safety.domain.services.GeofenceDecisionEvaluator;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The pure geofence rules (S17/U09) in isolation, with a fixed evaluation instant: freshness, accuracy, and
 * the strict (fail-closed) border rule. No Spring, no clock bean — the {@code now} is passed in, which is
 * exactly what makes the decision deterministic.
 */
class GeofenceDecisionEvaluatorTest {

    private static final Instant NOW = Instant.parse("2026-10-01T12:00:00Z");
    private static final GeofenceDecisionEvaluator EVALUATOR = new GeofenceDecisionEvaluator();

    private static GeofencePolicy policy(double centerLatitude, double centerLongitude, double radiusMeters) {
        return new GeofencePolicy(new CreateGeofencePolicyCommand(1L, 10L, centerLatitude, centerLongitude,
                radiusMeters), 1);
    }

    private static TrackedPosition position(double latitude, double longitude, Double accuracyMeters,
                                            Instant recordedAt) {
        return new TrackedPosition(latitude, longitude, accuracyMeters, recordedAt, 55L);
    }

    @Test
    void aFreshAccuratePositionInsideTheRadiusIsAuthorized() {
        var decision = EVALUATOR.evaluate(policy(10.0, 20.0, 1000.0),
                position(10.0, 20.0, 10.0, NOW), NOW);

        assertThat(decision).isInstanceOf(GeofenceDecision.Authorized.class);
        assertThat(decision.authorized()).isTrue();
        assertThat(decision.reason()).isNull();
    }

    @Test
    void aPositionExactlyOnTheBorderIsBlocked() {
        // distance 0 + accuracy 50 == radius 50 -> border -> blocked (strict inequality authorizes).
        var decision = EVALUATOR.evaluate(policy(10.0, 20.0, 50.0),
                position(10.0, 20.0, 50.0, NOW), NOW);

        assertThat(decision.authorized()).isFalse();
        assertThat(decision.reason()).isEqualTo(GeofenceBlockReason.OUTSIDE);
    }

    @Test
    void aPositionJustInsideTheBorderIsAuthorized() {
        var decision = EVALUATOR.evaluate(policy(10.0, 20.0, 50.5),
                position(10.0, 20.0, 50.0, NOW), NOW);

        assertThat(decision.authorized()).isTrue();
    }

    @Test
    void aPositionOutsideTheRadiusIsBlocked() {
        // ~1.1 km north of the centre, radius 100 m.
        var decision = EVALUATOR.evaluate(policy(10.0, 20.0, 100.0),
                position(10.01, 20.0, 10.0, NOW), NOW);

        assertThat(decision.authorized()).isFalse();
        assertThat(decision.reason()).isEqualTo(GeofenceBlockReason.OUTSIDE);
    }

    @Test
    void aStalePositionIsBlockedEvenWhenInside() {
        var stale = position(10.0, 20.0, 10.0, NOW.minusSeconds(301));
        var decision = EVALUATOR.evaluate(policy(10.0, 20.0, 1000.0), stale, NOW);

        assertThat(decision.authorized()).isFalse();
        assertThat(decision.reason()).isEqualTo(GeofenceBlockReason.STALE);
    }

    @Test
    void aPositionExactlyOnTheFreshnessWindowIsStillFresh() {
        var onWindow = position(10.0, 20.0, 10.0, NOW.minusSeconds(300));
        assertThat(EVALUATOR.evaluate(policy(10.0, 20.0, 1000.0), onWindow, NOW).authorized()).isTrue();
    }

    @Test
    void aPositionDatedBeyondTheClockSkewInTheFutureIsBlockedEvenWhenInside() {
        // A client clock far ahead would otherwise look "fresh" forever and authorize indefinitely.
        var future = position(10.0, 20.0, 10.0, NOW.plusSeconds(121));
        var decision = EVALUATOR.evaluate(policy(10.0, 20.0, 1000.0), future, NOW);

        assertThat(decision.authorized()).isFalse();
        assertThat(decision.reason()).isEqualTo(GeofenceBlockReason.FUTURE_TIMESTAMP);
    }

    @Test
    void aPositionExactlyOnTheClockSkewIsStillAccepted() {
        var onSkew = position(10.0, 20.0, 10.0, NOW.plusSeconds(120));
        assertThat(EVALUATOR.evaluate(policy(10.0, 20.0, 1000.0), onSkew, NOW).authorized()).isTrue();
    }

    @Test
    void aPositionWithAccuracyWorseThanFiftyMetresIsBlocked() {
        var decision = EVALUATOR.evaluate(policy(10.0, 20.0, 1000.0),
                position(10.0, 20.0, 51.0, NOW), NOW);

        assertThat(decision.authorized()).isFalse();
        assertThat(decision.reason()).isEqualTo(GeofenceBlockReason.INACCURATE);
    }

    @Test
    void accuracyExactlyAtTheLimitIsAccepted() {
        var decision = EVALUATOR.evaluate(policy(10.0, 20.0, 1000.0),
                position(10.0, 20.0, 50.0, NOW), NOW);

        assertThat(decision.authorized()).isTrue();
    }

    @Test
    void anUnknownAccuracyIsBlocked() {
        var decision = EVALUATOR.evaluate(policy(10.0, 20.0, 1000.0),
                position(10.0, 20.0, null, NOW), NOW);

        assertThat(decision.authorized()).isFalse();
        assertThat(decision.reason()).isEqualTo(GeofenceBlockReason.INACCURATE);
    }
}
