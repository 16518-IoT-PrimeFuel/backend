package com.primefuel.fulltank.platform.replenishment.domain.model.valueobjects;

import java.time.Instant;

/**
 * The immutable, explainable result of evaluating one tank level against its policy. It records not only
 * what to do but the numbers that justify it, so a shadow decision can be audited after the fact.
 *
 * @param episodeKey        present only when the decision opens or re-arms an episode
 * @param requestedVolume   the volume a request would carry; present only for {@code OPEN_EPISODE}
 * @param evaluatedAt       the injected-clock instant of the evaluation
 */
public record RefillDecision(
        RefillDecisionType type,
        Long tankId,
        Long organizationId,
        double levelPercent,
        double lowLevelPercent,
        double rearmPercent,
        String episodeKey,
        Double requestedVolume,
        String unit,
        Instant evaluatedAt) {

    public boolean wouldCreateRequest() {
        return type == RefillDecisionType.OPEN_EPISODE && requestedVolume != null && requestedVolume > 0;
    }
}
