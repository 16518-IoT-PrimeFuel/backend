package com.primefuel.fulltank.platform.fulfillment.api;

/**
 * Outbound port of the delivery module (S15/T15-B). The commercial side effects of a delivery reach into
 * {@code fleet}/{@code supply}/{@code inventory}/{@code ordering}/{@code equipment}; they live in the
 * composition root ({@code applicationflows}) behind this port, so {@code fulfillment} owns no foreign
 * repository. Both run inside the caller's transaction.
 */
public interface DeliveryIntegration {

    /**
     * A physical close: end the fleet reservation, consume the supply reservation (and its stock), refuel the
     * order's legacy equipment and settle the order ({@code PENDING_PAYMENT}).
     */
    void applyCompletionEffects(CompletionEffectsCommand command);

    /** A failed or cancelled delivery: free the fleet and supply it was holding. */
    void releaseReservations(String assignmentReference);

    record CompletionEffectsCommand(
            Long orderId,
            Long driverId,
            Long vehicleId,
            String assignmentReference,
            double deliveredVolume) {
    }
}
