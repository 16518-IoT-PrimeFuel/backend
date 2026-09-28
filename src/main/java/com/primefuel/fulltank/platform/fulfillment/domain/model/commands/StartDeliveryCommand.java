package com.primefuel.fulltank.platform.fulfillment.domain.model.commands;

/** Starts the physical execution of an already assigned delivery ({@code ASSIGNED → STARTED}). */
public record StartDeliveryCommand(Long deliveryId) {
}
