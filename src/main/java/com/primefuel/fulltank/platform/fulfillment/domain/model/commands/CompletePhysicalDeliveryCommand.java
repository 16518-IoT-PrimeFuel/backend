package com.primefuel.fulltank.platform.fulfillment.domain.model.commands;

/**
 * Closes the physical delivery with its evidence: the delivered volume (U11, 2026-09-22 — a numeric
 * volume, no photo/signature). Partial deliveries are legal ({@code deliveredVolume ≤ requestedVolume});
 * both values are kept and are never forced to match.
 */
public record CompletePhysicalDeliveryCommand(Long deliveryId, Double deliveredVolume) {
}
