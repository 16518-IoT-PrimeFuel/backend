package com.primefuel.fulltank.platform.equipment.domain.services;

/**
 * Decides whether a legacy Equipment row is classifiable as a Tank. The rule is deliberately narrow
 * (S06: only classifiable equipment creates a Tank, no mass rename): the asset must declare a positive
 * tank capacity and a fuel type. Everything else stays untouched as generic Equipment.
 */
public final class TankEligibility {

    private TankEligibility() {
    }

    public static boolean isMappable(Double tankCapacity, boolean hasFuelType) {
        return tankCapacity != null && tankCapacity > 0 && hasFuelType;
    }
}
