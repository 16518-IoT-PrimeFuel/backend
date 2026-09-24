package com.primefuel.fulltank.platform.ordering.interfaces.rest;

import com.primefuel.fulltank.platform.iam.interfaces.acl.TenantAccess;
import com.primefuel.fulltank.platform.ordering.application.internal.commandservices.FuelRequestService;
import com.primefuel.fulltank.platform.ordering.application.ports.FuelRequestData;
import com.primefuel.fulltank.platform.ordering.interfaces.rest.resources.FuelRequestResource;
import com.primefuel.fulltank.platform.ordering.interfaces.rest.resources.RejectFuelRequestResource;
import com.primefuel.fulltank.platform.ordering.interfaces.rest.transform.FuelOrderResourceFromEntityAssembler;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v2/provider-companies/{providerId}/replenishment-requests")
public class ProviderReplenishmentRequestsController {
    private final FuelRequestService service;
    private final TenantAccess access;

    public ProviderReplenishmentRequestsController(FuelRequestService service, TenantAccess access) {
        this.service = service;
        this.access = access;
    }

    @PostMapping("/{requestId}/accept")
    public ResponseEntity<?> accept(@PathVariable Long providerId, @PathVariable Long requestId) {
        if (!access.ownsProvider(providerId)) return ResponseEntity.status(403).build();
        try {
            var request = service.findById(requestId).filter(item -> providerId.equals(item.providerId()))
                    .orElseThrow(() -> new IllegalArgumentException("Request is outside provider tenant"));
            return ResponseEntity.ok(FuelOrderResourceFromEntityAssembler.toResourceFromEntity(service.accept(request.id())));
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return ResponseEntity.badRequest().body(java.util.Map.of("error", exception.getMessage()));
        }
    }

    @PostMapping("/{requestId}/reject")
    public ResponseEntity<?> reject(@PathVariable Long providerId, @PathVariable Long requestId,
                                    @RequestBody RejectFuelRequestResource resource) {
        if (!access.ownsProvider(providerId)) return ResponseEntity.status(403).build();
        try {
            var request = service.findById(requestId).filter(item -> providerId.equals(item.providerId()))
                    .orElseThrow(() -> new IllegalArgumentException("Request is outside provider tenant"));
            return ResponseEntity.ok(toResource(service.reject(request.id(), resource.reason())));
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return ResponseEntity.badRequest().body(java.util.Map.of("error", exception.getMessage()));
        }
    }

    private static FuelRequestResource toResource(FuelRequestData request) {
        return new FuelRequestResource(request.id(), request.buyerCompanyId(), request.providerId(), request.equipmentId(),
                request.fuelProductId(), request.fuelType(), request.productName(), request.quantity(), request.unit(),
                request.unitPrice(), request.deliveryAddress(), request.deliveryDate(), request.status(), request.source(),
                request.rejectionReason(), request.createdAt(), request.updatedAt());
    }
}
