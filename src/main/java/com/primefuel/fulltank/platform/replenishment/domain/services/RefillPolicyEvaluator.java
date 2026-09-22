package com.primefuel.fulltank.platform.replenishment.domain.services;

import com.primefuel.fulltank.platform.replenishment.domain.model.aggregates.RefillEpisode;
import com.primefuel.fulltank.platform.replenishment.domain.model.valueobjects.RefillDecision;
import com.primefuel.fulltank.platform.replenishment.domain.model.valueobjects.RefillDecisionType;
import com.primefuel.fulltank.platform.replenishment.domain.model.valueobjects.RefillThresholds;

import java.time.Instant;
import java.util.Optional;

/**
 * The pure heart of S09: given a tank level, its policy thresholds, the currently open episode and
 * whether a request is already active, it decides what to do — deterministically and with an injected
 * instant. It touches no infrastructure, so it can be exercised exhaustively without a database.
 *
 * <p>The two approved business rules live here: the 20% threshold (U03) and the +10 points hysteresis
 * band (U04) that stops noise near the threshold from producing duplicate requests (risk R05).
 */
public final class RefillPolicyEvaluator {

    private static final String EPISODE_PREFIX = "refill:";

    private RefillPolicyEvaluator() {
    }

    public static RefillDecision evaluate(Long tankId,
                                          Long organizationId,
                                          int policyVersion,
                                          double capacity,
                                          double level,
                                          double targetLevel,
                                          String unit,
                                          RefillThresholds thresholds,
                                          Optional<RefillEpisode> openEpisode,
                                          boolean hasPendingRequest,
                                          Instant now) {
        return evaluate(tankId, organizationId, policyVersion, capacity, level, targetLevel, unit, thresholds,
                openEpisode, hasPendingRequest, null, now);
    }

    public static RefillDecision evaluate(Long tankId,
                                          Long organizationId,
                                          int policyVersion,
                                          double capacity,
                                          double level,
                                          double targetLevel,
                                          String unit,
                                          RefillThresholds thresholds,
                                          Optional<RefillEpisode> openEpisode,
                                          boolean hasPendingRequest,
                                          String episodeKeySeed,
                                          Instant now) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Tank capacity must be positive");
        }
        if (level < 0 || level > capacity) {
            throw new IllegalArgumentException("Level must be between 0 and the tank capacity");
        }
        if (now == null) {
            throw new IllegalArgumentException("An evaluation instant is required");
        }

        var levelPercent = level / capacity * 100.0;

        if (openEpisode.isPresent()) {
            var episode = openEpisode.get();
            if (thresholds.atOrAboveRearm(levelPercent)) {
                return rearm(tankId, organizationId, levelPercent, thresholds, episode.getEpisodeKey(), now);
            }
            // Still inside the hysteresis band: the open episode keeps suppressing new requests.
            return noAction(tankId, organizationId, levelPercent, thresholds, now);
        }

        if (!thresholds.atOrBelowLowLevel(levelPercent)) {
            return noAction(tankId, organizationId, levelPercent, thresholds, now);
        }
        if (hasPendingRequest) {
            return suppressed(tankId, organizationId, levelPercent, thresholds, now);
        }

        var requestedVolume = targetLevel - level;
        if (requestedVolume <= 0) {
            // Nothing to request (target at or below the current level): a decision, but not a request.
            return noAction(tankId, organizationId, levelPercent, thresholds, now);
        }
        return open(tankId, organizationId, levelPercent, thresholds,
                episodeKey(tankId, policyVersion, episodeKeySeed, now), requestedVolume, unit, now);
    }

    private static String episodeKey(Long tankId, int policyVersion, String seed, Instant now) {
        if (seed != null && !seed.isBlank()) {
            // Stable per triggering reading, so a retried reading cannot open a second episode.
            return EPISODE_PREFIX + tankId + ":" + seed;
        }
        return EPISODE_PREFIX + tankId + ":" + policyVersion + ":" + now.toEpochMilli();
    }

    private static RefillDecision open(Long tankId, Long organizationId, double levelPercent,
                                       RefillThresholds thresholds, String episodeKey,
                                       double requestedVolume, String unit, Instant now) {
        return new RefillDecision(RefillDecisionType.OPEN_EPISODE, tankId, organizationId, levelPercent,
                thresholds.lowLevelPercent(), thresholds.rearmPercent(), episodeKey, requestedVolume, unit, now);
    }

    private static RefillDecision rearm(Long tankId, Long organizationId, double levelPercent,
                                        RefillThresholds thresholds, String episodeKey, Instant now) {
        return new RefillDecision(RefillDecisionType.REARM_EPISODE, tankId, organizationId, levelPercent,
                thresholds.lowLevelPercent(), thresholds.rearmPercent(), episodeKey, null, null, now);
    }

    private static RefillDecision noAction(Long tankId, Long organizationId, double levelPercent,
                                           RefillThresholds thresholds, Instant now) {
        return new RefillDecision(RefillDecisionType.NO_ACTION, tankId, organizationId, levelPercent,
                thresholds.lowLevelPercent(), thresholds.rearmPercent(), null, null, null, now);
    }

    private static RefillDecision suppressed(Long tankId, Long organizationId, double levelPercent,
                                             RefillThresholds thresholds, Instant now) {
        return new RefillDecision(RefillDecisionType.SUPPRESSED_PENDING_REQUEST, tankId, organizationId,
                levelPercent, thresholds.lowLevelPercent(), thresholds.rearmPercent(), null, null, null, now);
    }
}
