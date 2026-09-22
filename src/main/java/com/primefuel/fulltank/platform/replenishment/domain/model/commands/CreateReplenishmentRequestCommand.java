package com.primefuel.fulltank.platform.replenishment.domain.model.commands;

import com.primefuel.fulltank.platform.replenishment.domain.model.valueobjects.ReplenishmentSource;

public record CreateReplenishmentRequestCommand(
        Long organizationId,
        Long customerAccountId,
        Long tankId,
        Long providerId,
        Long fuelProductId,
        Double quantity,
        String unit,
        ReplenishmentSource source,
        String episodeKey) {
}
