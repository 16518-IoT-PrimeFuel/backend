package com.primefuel.fulltank.platform.fulfillment.interfaces.rest;

import com.primefuel.fulltank.platform.fulfillment.application.internal.commandservices.TrackingCommandService;
import com.primefuel.fulltank.platform.fulfillment.application.queryservices.DeliveryQueryService;
import com.primefuel.fulltank.platform.fulfillment.domain.model.queries.GetDeliveryByIdQuery;
import com.primefuel.fulltank.platform.fulfillment.interfaces.rest.resources.TrackingPointResource;
import com.primefuel.fulltank.platform.iam.interfaces.acl.TenantAccess;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping(value = "/api/v2/deliveries/{deliveryId}/tracking", produces = MediaType.APPLICATION_JSON_VALUE)
public class TrackingController {
    private final TrackingCommandService service;
    private final DeliveryQueryService deliveries;
    private final TenantAccess access;

    public TrackingController(TrackingCommandService service, DeliveryQueryService deliveries, TenantAccess access) {
        this.service = service;
        this.deliveries = deliveries;
        this.access = access;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> append(@PathVariable Long deliveryId,
                                                       @Valid @RequestBody TrackingPointResource resource) {
        var delivery = deliveries.handle(new GetDeliveryByIdQuery(deliveryId)).orElse(null);
        if (delivery == null || !access.ownsProvider(delivery.getProviderId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        try {
            var accepted = service.append(resource.eventId(), deliveryId, resource.recordedAt(),
                    resource.latitude(), resource.longitude(), resource.speedKph());
            return ResponseEntity.ok(Map.of("accepted", accepted, "eventId", resource.eventId()));
        } catch (IllegalStateException exception) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", exception.getMessage()));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
        }
    }
}
