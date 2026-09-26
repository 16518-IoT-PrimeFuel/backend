package com.primefuel.fulltank.platform.fulfillment.infrastructure.persistence.jpa.adapters;

import com.primefuel.fulltank.platform.fulfillment.application.ports.TrackingPointData;
import com.primefuel.fulltank.platform.fulfillment.application.ports.TrackingQueryPort;
import com.primefuel.fulltank.platform.fulfillment.infrastructure.persistence.jpa.entities.DeliveryTrackingPersistenceEntity;
import com.primefuel.fulltank.platform.fulfillment.infrastructure.persistence.jpa.repositories.DeliveryTrackingJpaRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class TrackingQueryPortAdapter implements TrackingQueryPort {
    private final DeliveryTrackingJpaRepository repository;

    public TrackingQueryPortAdapter(DeliveryTrackingJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<TrackingPointData> findByDeliveryId(Long deliveryId) {
        return repository.findByDeliveryIdOrderByRecordedAtAsc(deliveryId).stream().map(TrackingQueryPortAdapter::toData).toList();
    }

    @Override
    public Optional<TrackingPointData> findLatestByDeliveryId(Long deliveryId) {
        return repository.findTopByDeliveryIdOrderByRecordedAtDesc(deliveryId).map(TrackingQueryPortAdapter::toData);
    }

    private static TrackingPointData toData(DeliveryTrackingPersistenceEntity point) {
        return new TrackingPointData(point.getEventId(), point.getRecordedAt(), point.getLatitude(),
                point.getLongitude(), point.getSpeedKph());
    }
}
