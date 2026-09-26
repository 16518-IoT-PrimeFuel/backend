package com.primefuel.fulltank.platform.fulfillment.application.internal.commandservices;

import com.primefuel.fulltank.platform.fulfillment.application.ports.GeofenceStore;
import com.primefuel.fulltank.platform.fulfillment.domain.repositories.DeliveryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Service
public class GeofenceCommandService {
    private final DeliveryRepository deliveries;
    private final GeofenceStore geofences;
    private final Clock clock = Clock.systemUTC();

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
        return evaluate(deliveryId, latitude, longitude, null, null);
    }

    public GeofenceDecision evaluate(Long deliveryId, Double latitude, Double longitude,
                                     Instant capturedAt, Double accuracyMeters) {
        var geofence = geofences.active(deliveryId);
        if (geofence == null) throw new IllegalStateException("Geofence not configured");
        var distance = distanceMeters(geofence.latitude(), geofence.longitude(), latitude, longitude);
        if (capturedAt == null || accuracyMeters == null) {
            return new GeofenceDecision(false, geofence.version(), distance, "MISSING_EVIDENCE");
        }
        var now = Instant.now(clock);
        if (capturedAt.isBefore(now.minus(Duration.ofMinutes(5))) || capturedAt.isAfter(now.plusSeconds(30))) {
            return new GeofenceDecision(false, geofence.version(), distance, "STALE");
        }
        if (accuracyMeters <= 0 || accuracyMeters > 100) {
            return new GeofenceDecision(false, geofence.version(), distance, "INACCURATE");
        }
        var inside = distance <= geofence.radiusMeters();
        return new GeofenceDecision(inside, geofence.version(), distance, inside ? "INSIDE" : "OUTSIDE");
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
