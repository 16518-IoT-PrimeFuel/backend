package com.primefuel.fulltank.platform.replenishment.domain.model.valueobjects;

/**
 * The closed set of outcomes a single evaluation can produce. Keeping the vocabulary explicit is what
 * makes the shadow-mode decisions "stable and explainable" (S09/T09-A).
 */
public enum RefillDecisionType {

    /** The level fell at or below the threshold and no episode was active: a refill is needed. */
    OPEN_EPISODE,

    /** An open episode recovered beyond the hysteresis band: the policy re-arms. */
    REARM_EPISODE,

    /** Nothing to do: the level is above threshold or the episode is still inside the hysteresis band. */
    NO_ACTION,

    /** A request for this tank is already active, so the invariant forbids opening another episode. */
    SUPPRESSED_PENDING_REQUEST
}
