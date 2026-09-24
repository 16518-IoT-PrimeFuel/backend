package com.primefuel.fulltank.platform.ordering.interfaces.rest;

import com.primefuel.fulltank.platform.ordering.application.internal.commandservices.FuelRequestService;
import com.primefuel.fulltank.platform.ordering.infrastructure.persistence.jpa.entities.FuelRequestPersistenceEntity;
import com.primefuel.fulltank.platform.ordering.interfaces.rest.resources.*;
import com.primefuel.fulltank.platform.ordering.interfaces.rest.transform.FuelOrderResourceFromEntityAssembler;
import com.primefuel.fulltank.platform.iam.interfaces.acl.TenantAccess;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;

@RestController
@RequestMapping("/api/v1/fuel-requests")
public class FuelRequestsController {
    private final FuelRequestService service;
    private final TenantAccess currentUserAccess;

    public FuelRequestsController(FuelRequestService service, TenantAccess currentUserAccess) {
        this.service = service;
        this.currentUserAccess = currentUserAccess;
    }

    @PostMapping
    @PreAuthorize("@currentUserAccess.ownsCompany(#resource.buyerCompanyId())")
    public ResponseEntity<FuelRequestResource> create(@RequestBody CreateFuelRequestResource resource) {
        return new ResponseEntity<>(toResource(service.create(resource)), HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<List<FuelRequestResource>> findAll(@RequestParam(required = false) Long buyerCompanyId,
                                                               @RequestParam(required = false) Long providerId) {
        if (buyerCompanyId != null && providerId != null) {
            return ResponseEntity.badRequest().build();
        }
        if (buyerCompanyId != null && !currentUserAccess.ownsCompany(buyerCompanyId)
                || providerId != null && !currentUserAccess.ownsProvider(providerId)
                || buyerCompanyId == null && providerId == null) {
            return ResponseEntity.status(403).build();
        }
        var requests = service.findAll(buyerCompanyId, providerId).stream()
                .map(FuelRequestsController::toResource).toList();
        return ResponseEntity.ok(requests);
    }

    @GetMapping("/{requestId}")
    public ResponseEntity<FuelRequestResource> findById(@PathVariable Long requestId) {
        return service.findById(requestId)
                .filter(request -> currentUserAccess.ownsCompanyOrProvider(
                        request.getBuyerCompanyId(), request.getProviderId()))
                .map(request -> ResponseEntity.ok(toResource(request)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/{requestId}/accept")
    public ResponseEntity<?> accept(@PathVariable Long requestId) {
        if (!ownsRequestAsProvider(requestId)) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(FuelOrderResourceFromEntityAssembler.toResourceFromEntity(service.accept(requestId)));
    }

    @PostMapping("/{requestId}/reject")
    public ResponseEntity<FuelRequestResource> reject(@PathVariable Long requestId,
                                                      @RequestBody RejectFuelRequestResource resource) {
        if (!ownsRequestAsProvider(requestId)) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(toResource(service.reject(requestId, resource.reason())));
    }

    private boolean ownsRequestAsProvider(Long requestId) {
        return service.findById(requestId)
                .filter(request -> currentUserAccess.ownsProvider(request.getProviderId()))
                .isPresent();
    }

    private static FuelRequestResource toResource(FuelRequestPersistenceEntity r) {
        return new FuelRequestResource(r.getId(), r.getBuyerCompanyId(), r.getProviderId(), r.getEquipmentId(),
                r.getFuelProductId(), r.getFuelType(), r.getProductName(), r.getQuantity(), r.getUnit(),
                r.getUnitPrice(), r.getDeliveryAddress(), r.getDeliveryDate(), r.getStatus(), r.getSource(),
                r.getRejectionReason(), r.getCreatedAt(), r.getUpdatedAt());
    }
}
