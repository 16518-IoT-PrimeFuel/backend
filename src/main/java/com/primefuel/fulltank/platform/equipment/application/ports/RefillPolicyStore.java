package com.primefuel.fulltank.platform.equipment.application.ports;

import java.util.Optional;

public interface RefillPolicyStore {
    RefillPolicyData savePolicy(RefillPolicyData policy);

    Optional<RefillPolicyData> findPolicy(Long tankId);

    Optional<RefillEpisodeData> findEpisode(String episodeKey);

    Optional<RefillEpisodeData> findOpenEpisode(Long tankId);

    RefillEpisodeData saveEpisode(RefillEpisodeData episode);
}
