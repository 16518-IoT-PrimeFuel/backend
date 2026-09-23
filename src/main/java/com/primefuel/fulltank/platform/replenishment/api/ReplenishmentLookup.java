package com.primefuel.fulltank.platform.replenishment.api;

import java.util.Optional;

public interface ReplenishmentLookup {

    Optional<ReplenishmentView> findById(Long requestId);

    Optional<ReplenishmentView> findByEpisodeKey(String episodeKey);

    /** The request correlated with a legacy order (T15-A: resolve the acceptance behind an order). */
    Optional<ReplenishmentView> findByOrderId(Long orderId);

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
