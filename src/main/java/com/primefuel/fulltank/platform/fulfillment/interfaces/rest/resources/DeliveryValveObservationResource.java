package com.primefuel.fulltank.platform.fulfillment.interfaces.rest.resources;

import java.time.Instant;

public record DeliveryValveObservationResource(
        Long id, String state, boolean unauthorized, String commandId, Instant recordedAt) {}
