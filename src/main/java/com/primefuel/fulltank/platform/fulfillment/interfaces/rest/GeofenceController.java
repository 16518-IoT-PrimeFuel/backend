package com.primefuel.fulltank.platform.fulfillment.interfaces.rest;

import com.primefuel.fulltank.platform.fulfillment.application.internal.commandservices.GeofenceCommandService;
import com.primefuel.fulltank.platform.fulfillment.application.queryservices.DeliveryQueryService;
import com.primefuel.fulltank.platform.fulfillment.domain.model.queries.GetDeliveryByIdQuery;
import com.primefuel.fulltank.platform.fulfillment.interfaces.rest.resources.GeofenceEvaluationResource;
import com.primefuel.fulltank.platform.fulfillment.interfaces.rest.resources.GeofenceResource;
import com.primefuel.fulltank.platform.iam.interfaces.acl.TenantAccess;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping(value = "/api/v2/deliveries/{deliveryId}/geofence", produces = MediaType.APPLICATION_JSON_VALUE)
public class GeofenceController {
    private final GeofenceCommandService service;
    private final DeliveryQueryService deliveries;
    private final TenantAccess access;

    public GeofenceController(GeofenceCommandService service, DeliveryQueryService deliveries, TenantAccess access) {
        this.service = service;
        this.deliveries = deliveries;
        this.access = access;
    }

    @PutMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> configure(@PathVariable Long deliveryId, @Valid @RequestBody GeofenceResource resource) {
        var delivery = deliveries.handle(new GetDeliveryByIdQuery(deliveryId)).orElse(null);
        if (delivery == null || !access.ownsProvider(delivery.getProviderId())) return ResponseEntity.notFound().build();
        try {
            return ResponseEntity.ok(service.configure(deliveryId, resource.latitude(), resource.longitude(), resource.radiusMeters()));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
        }
    }

    @PostMapping(value = "/evaluate", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> evaluate(@PathVariable Long deliveryId,
                                       @Valid @RequestBody GeofenceEvaluationResource resource) {
        var delivery = deliveries.handle(new GetDeliveryByIdQuery(deliveryId)).orElse(null);
        if (delivery == null || !access.ownsProvider(delivery.getProviderId())) return ResponseEntity.notFound().build();
        try {
            return ResponseEntity.ok(service.evaluate(deliveryId, resource.latitude(), resource.longitude()));
        } catch (IllegalStateException exception) {
            return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
        }
    }
}
