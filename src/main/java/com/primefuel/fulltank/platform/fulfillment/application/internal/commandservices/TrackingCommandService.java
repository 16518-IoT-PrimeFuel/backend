package com.primefuel.fulltank.platform.fulfillment.application.internal.commandservices;

import com.primefuel.fulltank.platform.fulfillment.application.ports.TrackingStore;
import com.primefuel.fulltank.platform.fulfillment.domain.model.valueobjects.DeliveryStatus;
import com.primefuel.fulltank.platform.fulfillment.domain.repositories.DeliveryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class TrackingCommandService {
    private final DeliveryRepository deliveries;
    private final TrackingStore tracking;

    public TrackingCommandService(DeliveryRepository deliveries, TrackingStore tracking) {
        this.deliveries = deliveries;
        this.tracking = tracking;
    }

    @Transactional
    public boolean append(String eventId, Long deliveryId, Instant recordedAt,
                          Double latitude, Double longitude, Double speedKph) {
        var delivery = deliveries.findById(deliveryId)
                .orElseThrow(() -> new IllegalArgumentException("Delivery not found"));
        if (delivery.getStatus() != DeliveryStatus.DISPATCHED) {
            throw new IllegalStateException("Tracking requires a dispatched delivery");
        }
        if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180
                || (speedKph != null && speedKph < 0)) {
            throw new IllegalArgumentException("Invalid tracking coordinates or speed");
        }
        return tracking.append(eventId, deliveryId, recordedAt, latitude, longitude, speedKph);
    }
}
