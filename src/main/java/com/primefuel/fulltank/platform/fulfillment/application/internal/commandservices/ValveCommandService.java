package com.primefuel.fulltank.platform.fulfillment.application.internal.commandservices;

import com.primefuel.fulltank.platform.fulfillment.application.ports.ValveCommandStore;
import com.primefuel.fulltank.platform.fulfillment.domain.repositories.DeliveryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class ValveCommandService {
    private final DeliveryRepository deliveries;
    private final GeofenceCommandService geofences;
    private final ValveCommandStore commands;

    public ValveCommandService(DeliveryRepository deliveries, GeofenceCommandService geofences,
                                ValveCommandStore commands) {
        this.deliveries = deliveries;
        this.geofences = geofences;
        this.commands = commands;
    }

    @Transactional
    public boolean issue(String commandId, Long deliveryId, String desiredState,
                         Double latitude, Double longitude, Instant capturedAt, Double accuracyMeters) {
        var delivery = deliveries.findById(deliveryId)
                .orElseThrow(() -> new IllegalArgumentException("Delivery not found"));
        if (!"OPEN".equals(desiredState) && !"CLOSE".equals(desiredState)) {
            throw new IllegalArgumentException("Unsupported valve state");
        }
        if (!geofences.evaluate(deliveryId, latitude, longitude, capturedAt, accuracyMeters).inside()) {
            throw new IllegalStateException("Valve command outside geofence");
        }
        if (commands.exists(commandId)) return false;
        commands.create(commandId, delivery.getId(), desiredState, Instant.now());
        return true;
    }

    @Transactional
    public boolean acknowledge(String commandId, String ackId) {
        if (ackId == null || ackId.isBlank()) throw new IllegalArgumentException("ACK id is required");
        return commands.acknowledge(commandId, ackId, Instant.now());
    }
}
