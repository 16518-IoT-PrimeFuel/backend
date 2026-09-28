package com.primefuel.fulltank.platform.replenishment.application.internal.queryservices;

import com.primefuel.fulltank.platform.replenishment.application.queryservices.RefillPolicyQueryService;
import com.primefuel.fulltank.platform.replenishment.domain.model.aggregates.RefillEpisode;
import com.primefuel.fulltank.platform.replenishment.domain.model.aggregates.RefillPolicy;
import com.primefuel.fulltank.platform.replenishment.domain.model.queries.GetRefillEpisodesByTankQuery;
import com.primefuel.fulltank.platform.replenishment.domain.model.queries.GetRefillPolicyByTankQuery;
import com.primefuel.fulltank.platform.replenishment.domain.repositories.RefillEpisodeRepository;
import com.primefuel.fulltank.platform.replenishment.domain.repositories.RefillPolicyRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class RefillPolicyQueryServiceImpl implements RefillPolicyQueryService {

    private final RefillPolicyRepository policyRepository;
    private final RefillEpisodeRepository episodeRepository;

    public RefillPolicyQueryServiceImpl(RefillPolicyRepository policyRepository,
                                        RefillEpisodeRepository episodeRepository) {
        this.policyRepository = policyRepository;
        this.episodeRepository = episodeRepository;
    }

    @Override
    public Optional<RefillPolicy> handle(GetRefillPolicyByTankQuery query) {
        if (query.tankId() == null) {
            return Optional.empty();
        }
        return policyRepository.findByTankId(query.tankId());
    }

    @Override
    public List<RefillEpisode> handle(GetRefillEpisodesByTankQuery query) {
        if (query.tankId() == null) {
            return List.of();
        }
        return episodeRepository.findByTankId(query.tankId());
    }
}
