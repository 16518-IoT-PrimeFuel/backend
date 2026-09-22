package com.primefuel.fulltank.platform.replenishment;

import com.primefuel.fulltank.platform.replenishment.domain.model.aggregates.RefillEpisode;
import com.primefuel.fulltank.platform.replenishment.domain.model.valueobjects.RefillDecision;
import com.primefuel.fulltank.platform.replenishment.domain.model.valueobjects.RefillDecisionType;
import com.primefuel.fulltank.platform.replenishment.domain.model.valueobjects.RefillThresholds;
import com.primefuel.fulltank.platform.replenishment.domain.services.RefillPolicyEvaluator;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Boundary, hysteresis and clock behaviour of the pure policy evaluator (S09/T09-A). No Spring, no
 * database: every case pins the level, the thresholds and the instant.
 */
class RefillPolicyEvaluatorTest {

    private static final Long TANK = 20L;
    private static final Long ORG = 1L;
    private static final int POLICY_VERSION = 1;
    private static final double CAPACITY = 500.0;
    private static final Instant NOW = Instant.parse("2026-09-22T10:00:00Z");
    private static final String UNIT = "LITRE";

    private final RefillThresholds defaults = RefillThresholds.defaults();

    private RefillDecision evaluate(double level, Optional<RefillEpisode> openEpisode, boolean pending) {
        return RefillPolicyEvaluator.evaluate(TANK, ORG, POLICY_VERSION, CAPACITY, level, CAPACITY,
                UNIT, defaults, openEpisode, pending, NOW);
    }

    private RefillEpisode anOpenEpisode() {
        return new RefillEpisode("refill:20:1:0", TANK, ORG, POLICY_VERSION, 10.0, 50.0, CAPACITY, UNIT,
                NOW.minusSeconds(60));
    }

    @Test
    void defaultsEncodeTheApprovedBusinessRules() {
        assertThat(defaults.lowLevelPercent()).isEqualTo(20.0);
        assertThat(defaults.hysteresisPercent()).isEqualTo(10.0);
        assertThat(defaults.rearmPercent()).isEqualTo(30.0);
    }

    @Test
    void opensAnEpisodeExactlyAtTheThreshold() {
        var decision = evaluate(100.0, Optional.empty(), false); // 20% of 500

        assertThat(decision.type()).isEqualTo(RefillDecisionType.OPEN_EPISODE);
        assertThat(decision.levelPercent()).isEqualTo(20.0);
        assertThat(decision.wouldCreateRequest()).isTrue();
        assertThat(decision.requestedVolume()).isEqualTo(400.0);
        assertThat(decision.episodeKey()).isNotBlank();
        assertThat(decision.evaluatedAt()).isEqualTo(NOW);
    }

    @Test
    void doesNotOpenJustAboveTheThreshold() {
        var decision = evaluate(100.5, Optional.empty(), false); // 20.1%

        assertThat(decision.type()).isEqualTo(RefillDecisionType.NO_ACTION);
        assertThat(decision.wouldCreateRequest()).isFalse();
    }

    @Test
    void aPendingRequestSuppressesOpening() {
        var decision = evaluate(100.0, Optional.empty(), true);

        assertThat(decision.type()).isEqualTo(RefillDecisionType.SUPPRESSED_PENDING_REQUEST);
        assertThat(decision.episodeKey()).isNull();
    }

    @Test
    void anOpenEpisodeSuppressesDuplicatesInsideTheHysteresisBand() {
        var decision = evaluate(125.0, Optional.of(anOpenEpisode()), false); // 25%, inside 20..30

        assertThat(decision.type()).isEqualTo(RefillDecisionType.NO_ACTION);
        assertThat(decision.episodeKey()).isNull();
    }

    @Test
    void reArmsExactlyAtThresholdPlusHysteresis() {
        var decision = evaluate(150.0, Optional.of(anOpenEpisode()), false); // 30%

        assertThat(decision.type()).isEqualTo(RefillDecisionType.REARM_EPISODE);
        assertThat(decision.episodeKey()).isEqualTo("refill:20:1:0");
    }

    @Test
    void staysSuppressedJustBelowTheRearmPoint() {
        var decision = evaluate(149.95, Optional.of(anOpenEpisode()), false); // 29.99%

        assertThat(decision.type()).isEqualTo(RefillDecisionType.NO_ACTION);
    }

    @Test
    void anEpisodeIsNotOpenedWhenThereIsNothingToRequest() {
        // Target (full) already met by the current level, yet the tank is at the threshold.
        var decision = RefillPolicyEvaluator.evaluate(TANK, ORG, POLICY_VERSION, CAPACITY, 100.0, 100.0,
                UNIT, defaults, Optional.empty(), false, NOW);

        assertThat(decision.type()).isEqualTo(RefillDecisionType.NO_ACTION);
        assertThat(decision.wouldCreateRequest()).isFalse();
    }

    @Test
    void honoursPerTankThresholdOverrides() {
        var custom = new RefillThresholds(10.0, 5.0);

        var decision = RefillPolicyEvaluator.evaluate(TANK, ORG, POLICY_VERSION, CAPACITY, 75.0, CAPACITY,
                UNIT, custom, Optional.empty(), false, NOW); // 15%: above the 10% threshold

        assertThat(decision.type()).isEqualTo(RefillDecisionType.NO_ACTION);
        assertThat(decision.rearmPercent()).isEqualTo(15.0);
    }

    @Test
    void theEpisodeKeyIsStableForTheSameInstantAndMovesWithIt() {
        var first = evaluate(50.0, Optional.empty(), false);
        var second = evaluate(50.0, Optional.empty(), false);
        var later = RefillPolicyEvaluator.evaluate(TANK, ORG, POLICY_VERSION, CAPACITY, 50.0, CAPACITY,
                UNIT, defaults, Optional.empty(), false, NOW.plusSeconds(1));

        assertThat(first.episodeKey()).isEqualTo(second.episodeKey());
        assertThat(later.episodeKey()).isNotEqualTo(first.episodeKey());
    }

    @Test
    void rejectsImpossibleInputs() {
        assertThatThrownBy(() -> RefillPolicyEvaluator.evaluate(TANK, ORG, POLICY_VERSION, 0.0, 0.0, 0.0,
                UNIT, defaults, Optional.empty(), false, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> evaluate(9999.0, Optional.empty(), false))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
