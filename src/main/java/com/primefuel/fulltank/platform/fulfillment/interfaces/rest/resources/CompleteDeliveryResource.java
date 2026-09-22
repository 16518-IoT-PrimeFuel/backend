package com.primefuel.fulltank.platform.fulfillment.interfaces.rest.resources;

/**
 * Evidence for closing a delivery physically: the delivered volume (U11 — numeric only, no photo or
 * signature). It may be less than the requested volume; both values are kept.
 */
public record CompleteDeliveryResource(Double deliveredVolume) {
}
