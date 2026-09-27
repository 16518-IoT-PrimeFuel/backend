package com.primefuel.fulltank.platform.replenishment.domain.model.commands;

import com.primefuel.fulltank.platform.replenishment.domain.model.valueobjects.ReplenishmentSource;
import java.time.LocalDate;

public record CreateReplenishmentRequestCommand(
        Long organizationId,
        Long customerAccountId,
        Long tankId,
        Long providerId,
        Long fuelProductId,
        Double quantity,
        String unit,
        ReplenishmentSource source,
        String episodeKey,
        String deliveryAddress,
        LocalDate deliveryDate) {

    public CreateReplenishmentRequestCommand(Long organizationId, Long customerAccountId, Long tankId,
                                             Long providerId, Long fuelProductId, Double quantity,
                                             String unit, ReplenishmentSource source, String episodeKey) {
        this(organizationId, customerAccountId, tankId, providerId, fuelProductId, quantity,
                unit, source, episodeKey, null, null);
    }
}
