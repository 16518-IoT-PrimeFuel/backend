package com.primefuel.fulltank.platform.ordering.domain.model.valueobjects;

/**
 * Lifecycle of a fuel order. Dispatch and settlement: {@code DISPATCHED} is set by assignment, and
 * {@code PENDING_PAYMENT} is reached either by the physical close of the delivery or by the buyer's receipt
 * ({@code FuelOrder.receive()}, US-07), whichever comes first.
 *
 * <p>The settlement uses {@code PENDING_PAYMENT}; a delivery's {@code COMPLETED} is not mirrored into any order status.
 */
public enum OrderStatus {
    PENDING,
    CONFIRMED,
    DISPATCHED,
    PENDING_PAYMENT,
    PAID,
    CANCELLED
}
