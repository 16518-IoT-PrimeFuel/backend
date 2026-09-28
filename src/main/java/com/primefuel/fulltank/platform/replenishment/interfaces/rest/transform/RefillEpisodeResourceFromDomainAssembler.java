package com.primefuel.fulltank.platform.replenishment.interfaces.rest.transform;

import com.primefuel.fulltank.platform.replenishment.domain.model.aggregates.RefillEpisode;
import com.primefuel.fulltank.platform.replenishment.interfaces.rest.resources.RefillEpisodeResource;

public final class RefillEpisodeResourceFromDomainAssembler {

    private RefillEpisodeResourceFromDomainAssembler() {
    }

    public static RefillEpisodeResource toResourceFromDomain(RefillEpisode episode) {
        return new RefillEpisodeResource(
                episode.getId(),
                episode.getEpisodeKey(),
                episode.getTankId(),
                episode.getOrganizationId(),
                episode.getPolicyVersion(),
                episode.getStatus() == null ? null : episode.getStatus().name(),
                episode.getOpenedAt(),
                episode.getOpenedLevelPercent(),
                episode.getOpenedLevel(),
                episode.getTargetLevel(),
                episode.getRequestedVolume(),
                episode.getUnit(),
                episode.isRequestEmitted(),
                episode.getRequestId(),
                episode.getClosedAt(),
                episode.getClosedLevelPercent(),
                episode.getVersion());
    }
}
