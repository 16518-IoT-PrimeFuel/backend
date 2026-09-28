package com.primefuel.fulltank.platform.fulfillment.api;

import java.util.Optional;

/**
 * Read seam of the delivery module for the transport-tracking consumer (S16/T16-A). The {@code tracking}
 * module must never read a {@code Delivery} through its repository or entity, so this is the only public
 * way to learn <em>which driver is assigned to a delivery</em>.
 *
 * <p>The snapshot is the assignment left behind by S14/S15 ({@code Delivery.driverId}/{@code providerId}),
 * never a value sent by a client: the transport-evidence endpoint authorises the caller by resolving the
 * assigned driver here. There is no write surface — tracking observes, it does not assign.
 */
public interface DeliveryTrackingLookup {

    /** The current assignment of a delivery, if the delivery exists. */
    Optional<AssignedDeliverySnapshot> findAssignedDelivery(Long deliveryId);

    record AssignedDeliverySnapshot(
            Long deliveryId,
            Long orderId,
            Long providerId,
            Long driverId,
            String physicalState) {
    }
}
