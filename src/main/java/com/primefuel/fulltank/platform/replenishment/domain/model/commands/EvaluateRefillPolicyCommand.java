package com.primefuel.fulltank.platform.replenishment.domain.model.commands;

import java.time.Instant;

/**
 * Evaluates one tank level against its policy. The level and capacity travel together and in the same
 * unit so the evaluation is deterministic and does not depend on a second read.
 *
 * @param episodeKeySeed the identity of the reading that triggered the evaluation (device:channel:sequence).
 *                       When present it makes the episode key — and therefore the generated request —
 *                       stable across retries of the same reading. Null for direct/administrative calls.
 * @param evaluatedAt    the instant to evaluate at (the reading's capture time); null means "use the clock".
 */
public record EvaluateRefillPolicyCommand(
        Long tankId,
        Long organizationId,
        Long customerAccountId,
        Double level,
        String unit,
        Double capacity,
        String episodeKeySeed,
        Instant evaluatedAt) {
}
