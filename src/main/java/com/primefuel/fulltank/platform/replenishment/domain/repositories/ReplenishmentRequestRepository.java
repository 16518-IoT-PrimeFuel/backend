package com.primefuel.fulltank.platform.replenishment.domain.repositories;

import com.primefuel.fulltank.platform.replenishment.domain.model.aggregates.ReplenishmentRequest;

import java.util.List;
import java.util.Optional;

public interface ReplenishmentRequestRepository {
    Optional<ReplenishmentRequest> findById(Long id);
    List<ReplenishmentRequest> findByOrganizationId(Long organizationId);
    Optional<ReplenishmentRequest> findByEpisodeKey(String episodeKey);
    ReplenishmentRequest save(ReplenishmentRequest request);

    /** Flushes immediately so optimistic-lock conflicts surface inside the caller's transaction. */
    ReplenishmentRequest saveAndFlush(ReplenishmentRequest request);
}
