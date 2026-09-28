package com.primefuel.fulltank.platform.equipment.architecturefixture;

import com.primefuel.fulltank.platform.ordering.domain.repositories.FuelOrderRepository;

public class SeededBoundaryViolation {

    private FuelOrderRepository leakedDependency;

    public FuelOrderRepository leakedDependency() {
        return leakedDependency;
    }
}
