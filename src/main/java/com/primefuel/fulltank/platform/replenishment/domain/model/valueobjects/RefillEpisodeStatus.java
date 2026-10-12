package com.primefuel.fulltank.platform.replenishment.domain.model.valueobjects;

/**
 * An episode is born {@code OPEN} (a refill need exists). It leaves OPEN in one of two ways, and both let the
 * next low reading open a new episode:
 * <ul>
 *   <li>{@code REARMED}: the level recovers past the hysteresis band;</li>
 *   <li>{@code CLOSED}: the request it produced was rejected or cancelled (US-17). The decision is final for
 *       that request, so the tank may ask again.</li>
 * </ul>
 * Only OPEN holds the tank's open slot (see the persistence assembler).
 */
public enum RefillEpisodeStatus {
    OPEN,
    REARMED,
    CLOSED
}
