package com.primefuel.fulltank.platform.replenishment.domain.model.valueobjects;

/**
 * The explicit low-level policy (U03/U04): the level at which a refill episode opens and the extra band
 * it must recover before the policy re-arms. Both are expressed as a percentage of tank capacity.
 */
public record RefillThresholds(double lowLevelPercent, double hysteresisPercent) {

    /** U03: global default low-level threshold (20% of capacity), overridable per tank. */
    public static final double DEFAULT_LOW_LEVEL_PERCENT = 20.0;

    /** U04: the episode re-arms only once the level climbs this many points above the threshold. */
    public static final double DEFAULT_HYSTERESIS_PERCENT = 10.0;

    public RefillThresholds {
        if (!Double.isFinite(lowLevelPercent) || lowLevelPercent <= 0 || lowLevelPercent >= 100) {
            throw new IllegalArgumentException("Low level percent must be strictly between 0 and 100");
        }
        if (!Double.isFinite(hysteresisPercent) || hysteresisPercent <= 0) {
            throw new IllegalArgumentException("Hysteresis percent must be positive");
        }
        if (lowLevelPercent + hysteresisPercent > 100) {
            throw new IllegalArgumentException("Low level plus hysteresis cannot exceed 100");
        }
    }

    public static RefillThresholds defaults() {
        return new RefillThresholds(DEFAULT_LOW_LEVEL_PERCENT, DEFAULT_HYSTERESIS_PERCENT);
    }

    /** U04: the level the tank must reach before a new episode may open (30% with the defaults). */
    public double rearmPercent() {
        return lowLevelPercent + hysteresisPercent;
    }

    public boolean atOrBelowLowLevel(double levelPercent) {
        return levelPercent <= lowLevelPercent;
    }

    public boolean atOrAboveRearm(double levelPercent) {
        return levelPercent >= rearmPercent();
    }
}
