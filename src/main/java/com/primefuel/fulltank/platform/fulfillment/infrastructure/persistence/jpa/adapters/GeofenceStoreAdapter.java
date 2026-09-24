package com.primefuel.fulltank.platform.fulfillment.infrastructure.persistence.jpa.adapters;

import com.primefuel.fulltank.platform.fulfillment.application.ports.GeofenceStore;
import com.primefuel.fulltank.platform.fulfillment.infrastructure.persistence.jpa.entities.DeliveryGeofencePersistenceEntity;
import com.primefuel.fulltank.platform.fulfillment.infrastructure.persistence.jpa.repositories.DeliveryGeofenceJpaRepository;
import org.springframework.stereotype.Component;

@Component
public class GeofenceStoreAdapter implements GeofenceStore {
    private final DeliveryGeofenceJpaRepository repository;

    public GeofenceStoreAdapter(DeliveryGeofenceJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public GeofenceData upsert(Long deliveryId, Double latitude, Double longitude, Double radiusMeters) {
        var current = repository.findTopByDeliveryIdAndStatusOrderByVersionDesc(deliveryId, "ACTIVE").orElse(null);
        var next = new DeliveryGeofencePersistenceEntity();
        next.setDeliveryId(deliveryId);
        next.setVersion(current == null ? 1 : current.getVersion() + 1);
        next.setLatitude(latitude);
        next.setLongitude(longitude);
        next.setRadiusMeters(radiusMeters);
        next.setStatus("ACTIVE");
        if (current != null) {
            current.setStatus("SUPERSEDED");
            repository.save(current);
        }
        var saved = repository.save(next);
        return new GeofenceData(saved.getDeliveryId(), saved.getVersion(), saved.getLatitude(),
                saved.getLongitude(), saved.getRadiusMeters());
    }

    @Override
    public GeofenceData active(Long deliveryId) {
        return repository.findTopByDeliveryIdAndStatusOrderByVersionDesc(deliveryId, "ACTIVE")
                .map(entity -> new GeofenceData(entity.getDeliveryId(), entity.getVersion(), entity.getLatitude(),
                        entity.getLongitude(), entity.getRadiusMeters()))
                .orElse(null);
    }
}
