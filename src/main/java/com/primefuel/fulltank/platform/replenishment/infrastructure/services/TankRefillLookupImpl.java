package com.primefuel.fulltank.platform.replenishment.infrastructure.services;

import com.primefuel.fulltank.platform.replenishment.api.TankRefillLookup;
import com.primefuel.fulltank.platform.replenishment.domain.model.valueobjects.RefillThresholds;
import com.primefuel.fulltank.platform.replenishment.domain.repositories.RefillPolicyRepository;

import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class TankRefillLookupImpl implements TankRefillLookup {
    private final RefillPolicyRepository policies;

    public TankRefillLookupImpl(RefillPolicyRepository policies) {
        this.policies = policies;
    }

    public Optional<PolicySnapshot> findPolicy(Long tankId) {
        return policies.findByTankId(tankId)
                .map(
                        p ->
                                new PolicySnapshot(
                                        p.getOrganizationId(),
                                        p.getLowLevelPercent(),
                                        p.getFuelProductId(),
                                        p.getHysteresisPercent(),
                                        p.getTargetLevelPercent(),
                                        p.isAutoGenerateEnabled(),
                                        p.getProviderId()));
    }

    public double defaultLowLevelPercent() {
        return RefillThresholds.DEFAULT_LOW_LEVEL_PERCENT;
    }
}
