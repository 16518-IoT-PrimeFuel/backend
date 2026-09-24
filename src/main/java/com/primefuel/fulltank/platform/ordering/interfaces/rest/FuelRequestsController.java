package com.primefuel.fulltank.platform.ordering.interfaces.rest;

import com.primefuel.fulltank.platform.ordering.application.internal.commandservices.FuelRequestService;
import com.primefuel.fulltank.platform.ordering.application.ports.FuelRequestData;
import com.primefuel.fulltank.platform.ordering.domain.model.commands.CreateFuelRequestCommand;
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
        var command = new CreateFuelRequestCommand(resource.buyerCompanyId(), resource.providerId(),
                resource.equipmentId(), resource.fuelProductId(), resource.quantity(), resource.unit(),
                resource.deliveryAddress(), resource.deliveryDate(), resource.source());
        return new ResponseEntity<>(toResource(service.create(command)), HttpStatus.CREATED);
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
                        request.buyerCompanyId(), request.providerId()))
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
                .filter(request -> currentUserAccess.ownsProvider(request.providerId()))
                .isPresent();
    }

    private static FuelRequestResource toResource(FuelRequestData r) {
        return new FuelRequestResource(r.id(), r.buyerCompanyId(), r.providerId(), r.equipmentId(),
                r.fuelProductId(), r.fuelType(), r.productName(), r.quantity(), r.unit(), r.unitPrice(),
                r.deliveryAddress(), r.deliveryDate(), r.status(), r.source(), r.rejectionReason(),
                r.createdAt(), r.updatedAt());
    }
}
