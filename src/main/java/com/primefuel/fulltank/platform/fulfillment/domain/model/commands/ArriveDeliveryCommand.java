package com.primefuel.fulltank.platform.fulfillment.domain.model.commands;

/** Records arrival at the delivery site ({@code STARTED → ARRIVED}). */
public record ArriveDeliveryCommand(Long deliveryId) {
}
