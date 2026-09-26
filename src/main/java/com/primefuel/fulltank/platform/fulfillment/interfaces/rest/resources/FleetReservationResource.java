package com.primefuel.fulltank.platform.fulfillment.interfaces.rest.resources;

public record FleetReservationResource(String idempotencyKey, boolean reserved) {
}
