package com.primefuel.fulltank.platform.ordering.interfaces.rest.resources;

import java.time.LocalDate;

public record CreateFuelRequestResource(
        Long buyerCompanyId, Long providerId, Long equipmentId, Long fuelProductId,
        Double quantity, String unit, String deliveryAddress, LocalDate deliveryDate, String source) {
}
