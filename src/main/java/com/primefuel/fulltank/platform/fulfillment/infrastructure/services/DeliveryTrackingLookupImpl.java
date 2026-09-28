package com.primefuel.fulltank.platform.fulfillment.infrastructure.services;

import com.primefuel.fulltank.platform.fulfillment.api.DeliveryTrackingLookup;
import com.primefuel.fulltank.platform.fulfillment.domain.repositories.DeliveryRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Adapter implementing the delivery read seam for transport tracking (S16/T16-A). It reads the delivery's
 * own aggregate to expose the assigned driver and tenant; nothing here mutates state.
 */
@Component("deliveryTrackingLookup")
public class DeliveryTrackingLookupImpl implements DeliveryTrackingLookup {

    private final DeliveryRepository deliveryRepository;

    public DeliveryTrackingLookupImpl(DeliveryRepository deliveryRepository) {
        this.deliveryRepository = deliveryRepository;
    }

    @Override
    public Optional<AssignedDeliverySnapshot> findAssignedDelivery(Long deliveryId) {
        if (deliveryId == null) {
            return Optional.empty();
        }
        return deliveryRepository.findById(deliveryId).map(delivery -> new AssignedDeliverySnapshot(
                delivery.getId(), delivery.getOrderId(), delivery.getProviderId(), delivery.getDriverId(),
                delivery.currentPhysicalState().name()));
    }
}
