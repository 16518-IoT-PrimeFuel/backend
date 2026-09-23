package com.primefuel.fulltank.platform.fulfillment.api;

import com.primefuel.fulltank.platform.shared.application.result.ApplicationError;
import com.primefuel.fulltank.platform.shared.application.result.Result;

/**
 * Outbound port of the delivery module (S15/T15-B). The legacy v1 delivery path used to reach directly into
 * {@code fleet}/{@code inventory}/{@code ordering}/{@code equipment} repositories; that cross-module work now
 * lives in the composition root ({@code applicationflows}) behind this port, so {@code fulfillment} owns no
 * foreign repository anymore.
 *
 * <p>{@link #createDelivery} is the v1 {@code POST /deliveries} entry: it is <em>dual-mode</em> — an order
 * backed by an accepted replenishment request goes through the exclusive reservation orchestrator (the same
 * one v2 uses), while a direct legacy order keeps its original side effects. Either way the legacy order is
 * dispatched, so the v1 contract (and the golden path to {@code PENDING_PAYMENT}) is preserved.
 */
public interface DeliveryIntegration {

    /** v1 create; returns the id of the created delivery. Idempotent per order. */
    Result<Long, ApplicationError> createDelivery(CreateLegacyDeliveryCommand command);

    /**
     * The legacy v1 completion side effects that must not live inside {@code fulfillment}: release the
     * driver/tanker, end any fleet reservation, refuel the order's tank and settle the order. Runs inside the
     * caller's transaction.
     */
    void applyCompletionEffects(CompletionEffectsCommand command);

    record CreateLegacyDeliveryCommand(
            Long orderId,
            Long providerId,
            Long driverId,
            Long vehicleId,
            String scheduledDate,
            String notes) {
    }

    record CompletionEffectsCommand(
            Long orderId,
            Long driverId,
            Long vehicleId,
            String assignmentReference) {
    }
}
