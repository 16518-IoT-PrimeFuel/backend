package com.primefuel.fulltank.platform.ordering.domain.model.commands;

import java.time.LocalDate;

public record CreateFuelRequestCommand(
        Long buyerCompanyId,
        Long providerId,
        Long equipmentId,
        Long fuelProductId,
        Double quantity,
        String unit,
        String deliveryAddress,
        LocalDate deliveryDate,
        String source) {
}
