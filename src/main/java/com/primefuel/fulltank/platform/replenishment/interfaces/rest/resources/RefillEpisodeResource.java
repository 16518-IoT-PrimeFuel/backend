package com.primefuel.fulltank.platform.replenishment.interfaces.rest.resources;

import java.time.Instant;

public record RefillEpisodeResource(
        Long id,
        String episodeKey,
        Long tankId,
        Long organizationId,
        int policyVersion,
        String status,
        Instant openedAt,
        double openedLevelPercent,
        double openedLevel,
        double targetLevel,
        double requestedVolume,
        String unit,
        boolean requestEmitted,
        Long requestId,
        Instant closedAt,
        Double closedLevelPercent,
        int version) {
}
