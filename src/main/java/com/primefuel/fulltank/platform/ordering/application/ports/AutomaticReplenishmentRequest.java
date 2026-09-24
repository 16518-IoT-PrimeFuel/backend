package com.primefuel.fulltank.platform.ordering.application.ports;

import com.primefuel.fulltank.platform.ordering.domain.model.commands.CreateFuelRequestCommand;

public interface AutomaticReplenishmentRequest {
    FuelRequestData create(CreateFuelRequestCommand command, String idempotencyKey);
}
