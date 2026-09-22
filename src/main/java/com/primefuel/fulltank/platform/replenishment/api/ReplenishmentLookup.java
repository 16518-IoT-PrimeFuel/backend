package com.primefuel.fulltank.platform.replenishment.api;

import java.util.Optional;

public interface ReplenishmentLookup {

    Optional<ReplenishmentView> findById(Long requestId);

    Optional<ReplenishmentView> findByEpisodeKey(String episodeKey);

    record ReplenishmentView(
            Long id,
            Long organizationId,
            Long providerId,
            Long fuelProductId,
            Long tankId,
            double quantity,
            String unit,
            double unitPrice,
            String status,
            Long orderId) {
    }
}
