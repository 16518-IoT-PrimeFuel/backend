package com.primefuel.fulltank.platform.ordering.api;

import com.primefuel.fulltank.platform.shared.application.result.ApplicationError;
import com.primefuel.fulltank.platform.shared.application.result.Result;

import java.time.LocalDate;

/** Public ordering seam for creating an order without exposing ordering domain types to other modules. */
public interface FuelOrderCreation {

    Result<Long, ApplicationError> create(Command command);

    record Command(Long companyId, Long providerId, Long fuelProductId, Long equipmentId,
                   Double quantity, String deliveryAddress, LocalDate deliveryDate) {
    }
}
