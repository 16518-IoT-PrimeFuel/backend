package com.primefuel.fulltank.platform.replenishment.application.queryservices;

import com.primefuel.fulltank.platform.replenishment.domain.model.aggregates.RefillEpisode;
import com.primefuel.fulltank.platform.replenishment.domain.model.aggregates.RefillPolicy;
import com.primefuel.fulltank.platform.replenishment.domain.model.queries.GetRefillEpisodesByTankQuery;
import com.primefuel.fulltank.platform.replenishment.domain.model.queries.GetRefillPolicyByTankQuery;

import java.util.List;
import java.util.Optional;

public interface RefillPolicyQueryService {

    Optional<RefillPolicy> handle(GetRefillPolicyByTankQuery query);

    List<RefillEpisode> handle(GetRefillEpisodesByTankQuery query);
}
