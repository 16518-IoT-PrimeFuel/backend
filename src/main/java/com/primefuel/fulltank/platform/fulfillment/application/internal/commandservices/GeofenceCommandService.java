package com.primefuel.fulltank.platform.fulfillment.application.internal.commandservices;

import com.primefuel.fulltank.platform.fulfillment.application.ports.GeofenceStore;
import com.primefuel.fulltank.platform.fulfillment.domain.repositories.DeliveryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GeofenceCommandService {
    private final DeliveryRepository deliveries;
    private final GeofenceStore geofences;

    public GeofenceCommandService(DeliveryRepository deliveries, GeofenceStore geofences) {
        this.deliveries = deliveries;
        this.geofences = geofences;
    }

    @Transactional
    public GeofenceStore.GeofenceData configure(Long deliveryId, Double latitude, Double longitude, Double radiusMeters) {
        if (deliveries.findById(deliveryId).isEmpty()) throw new IllegalArgumentException("Delivery not found");
        if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180 || radiusMeters <= 0) {
            throw new IllegalArgumentException("Invalid geofence");
        }
        return geofences.upsert(deliveryId, latitude, longitude, radiusMeters);
    }

    public GeofenceDecision evaluate(Long deliveryId, Double latitude, Double longitude) {
        var geofence = geofences.active(deliveryId);
        if (geofence == null) throw new IllegalStateException("Geofence not configured");
        var distance = distanceMeters(geofence.latitude(), geofence.longitude(), latitude, longitude);
        return new GeofenceDecision(distance <= geofence.radiusMeters(), geofence.version(), distance);
    }

    private static double distanceMeters(double aLat, double aLon, double bLat, double bLon) {
        var earthRadius = 6_371_000d;
        var latitudeDelta = Math.toRadians(bLat - aLat);
        var longitudeDelta = Math.toRadians(bLon - aLon);
        var a = Math.sin(latitudeDelta / 2) * Math.sin(latitudeDelta / 2)
                + Math.cos(Math.toRadians(aLat)) * Math.cos(Math.toRadians(bLat))
                * Math.sin(longitudeDelta / 2) * Math.sin(longitudeDelta / 2);
        return earthRadius * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
