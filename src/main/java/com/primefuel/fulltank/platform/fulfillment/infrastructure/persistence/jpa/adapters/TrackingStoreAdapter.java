package com.primefuel.fulltank.platform.fulfillment.infrastructure.persistence.jpa.adapters;

import com.primefuel.fulltank.platform.fulfillment.application.ports.TrackingStore;
import com.primefuel.fulltank.platform.fulfillment.infrastructure.persistence.jpa.entities.DeliveryTrackingPersistenceEntity;
import com.primefuel.fulltank.platform.fulfillment.infrastructure.persistence.jpa.repositories.DeliveryTrackingJpaRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class TrackingStoreAdapter implements TrackingStore {
    private final DeliveryTrackingJpaRepository repository;

    public TrackingStoreAdapter(DeliveryTrackingJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean append(String eventId, Long deliveryId, Instant recordedAt,
                          Double latitude, Double longitude, Double speedKph) {
        if (repository.existsByEventId(eventId)) return false;
        var point = new DeliveryTrackingPersistenceEntity();
        point.setEventId(eventId);
        point.setDeliveryId(deliveryId);
        point.setRecordedAt(recordedAt);
        point.setLatitude(latitude);
        point.setLongitude(longitude);
        point.setSpeedKph(speedKph);
        repository.save(point);
        return true;
    }
}
