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

    /**
     * A failed or cancelled delivery gives its order back: a dispatched order returns to CONFIRMED (so it can be
     * assigned again) and the legacy driver/tanker status it set is freed. The request stays ACCEPTED and its
     * acceptance stays consumed, so the need is still live and the next assignment skips the consume gate.
     */
    void reopenOrder(Long orderId, Long driverId, Long vehicleId);

    /** Organization of the buyer behind the order (its replenishment request), the scope of its notifications. */
    java.util.Optional<Long> buyerOrganizationId(Long orderId);

    /** Organization of the distributor that owns the (legacy) provider id. */
    java.util.Optional<Long> providerOrganizationId(Long providerId);

    record CompletionEffectsCommand(
            Long orderId,
            Long driverId,
            Long vehicleId,
            String assignmentReference,
            double deliveredVolume) {
    }
}
