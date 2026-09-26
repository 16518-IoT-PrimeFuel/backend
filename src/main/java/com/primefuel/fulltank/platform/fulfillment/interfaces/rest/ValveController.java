package com.primefuel.fulltank.platform.fulfillment.interfaces.rest;

import com.primefuel.fulltank.platform.fulfillment.application.internal.commandservices.ValveCommandService;
import com.primefuel.fulltank.platform.fulfillment.application.queryservices.DeliveryQueryService;
import com.primefuel.fulltank.platform.fulfillment.domain.model.queries.GetDeliveryByIdQuery;
import com.primefuel.fulltank.platform.fulfillment.interfaces.rest.resources.ValveAckResource;
import com.primefuel.fulltank.platform.fulfillment.interfaces.rest.resources.ValveCommandResource;
import com.primefuel.fulltank.platform.iam.interfaces.acl.TenantAccess;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping(value = "/api/v2/deliveries/{deliveryId}/valve", produces = MediaType.APPLICATION_JSON_VALUE)
public class ValveController {
    private final ValveCommandService service;
    private final DeliveryQueryService deliveries;
    private final TenantAccess access;

    public ValveController(ValveCommandService service, DeliveryQueryService deliveries, TenantAccess access) {
        this.service = service;
        this.deliveries = deliveries;
        this.access = access;
    }

    @PostMapping(value = "/commands", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> issue(@PathVariable Long deliveryId, @Valid @RequestBody ValveCommandResource resource) {
        var delivery = deliveries.handle(new GetDeliveryByIdQuery(deliveryId)).orElse(null);
        if (delivery == null || !access.ownsProvider(delivery.getProviderId())) return ResponseEntity.notFound().build();
        try {
            return ResponseEntity.ok(Map.of("accepted", service.issue(resource.commandId(), deliveryId,
                    resource.desiredState(), resource.latitude(), resource.longitude(), resource.capturedAt(),
                    resource.accuracyMeters()), "status", "PENDING"));
        } catch (IllegalStateException exception) {
            return ResponseEntity.status(409).body(Map.of("error", exception.getMessage()));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
        }
    }

    @PostMapping(value = "/commands/{commandId}/ack", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> acknowledge(@PathVariable Long deliveryId, @PathVariable String commandId,
                                         @Valid @RequestBody ValveAckResource resource) {
        var delivery = deliveries.handle(new GetDeliveryByIdQuery(deliveryId)).orElse(null);
        if (delivery == null || !access.ownsProvider(delivery.getProviderId())) return ResponseEntity.notFound().build();
        return service.acknowledge(commandId, resource.ackId())
                ? ResponseEntity.ok(Map.of("status", "ACKED"))
                : ResponseEntity.notFound().build();
    }
}
