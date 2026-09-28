package com.primefuel.fulltank.platform.replenishment.domain.model.valueobjects;

/**
 * An episode is born {@code OPEN} (a refill need exists) and only becomes {@code REARMED} when the level
 * recovers past the hysteresis band, which is what lets the next low reading open a new episode.
 */
public enum RefillEpisodeStatus {
    OPEN,
    REARMED
}
