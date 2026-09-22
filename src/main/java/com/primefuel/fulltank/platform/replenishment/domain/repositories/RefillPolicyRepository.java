package com.primefuel.fulltank.platform.replenishment.domain.repositories;

import com.primefuel.fulltank.platform.replenishment.domain.model.aggregates.RefillPolicy;

import java.util.Optional;

public interface RefillPolicyRepository {
    Optional<RefillPolicy> findByTankId(Long tankId);
    RefillPolicy save(RefillPolicy policy);
}
