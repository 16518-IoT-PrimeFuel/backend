package com.primefuel.fulltank.platform.replenishment.domain.repositories;

import com.primefuel.fulltank.platform.replenishment.domain.model.aggregates.RefillEpisode;

import java.util.List;
import java.util.Optional;

public interface RefillEpisodeRepository {
    Optional<RefillEpisode> findOpenByTankId(Long tankId);
    Optional<RefillEpisode> findByEpisodeKey(String episodeKey);
    List<RefillEpisode> findByTankId(Long tankId);
    RefillEpisode save(RefillEpisode episode);

    /** Flushes immediately so a concurrent open is rejected inside the caller's transaction. */
    RefillEpisode saveAndFlush(RefillEpisode episode);
}
