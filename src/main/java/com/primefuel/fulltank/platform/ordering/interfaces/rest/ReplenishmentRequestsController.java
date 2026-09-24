package com.primefuel.fulltank.platform.ordering.interfaces.rest;

import com.primefuel.fulltank.platform.ordering.application.internal.commandservices.FuelRequestService;
import com.primefuel.fulltank.platform.ordering.interfaces.rest.resources.CreateReplenishmentRequestResource;
import com.primefuel.fulltank.platform.ordering.interfaces.rest.resources.FuelRequestResource;
import com.primefuel.fulltank.platform.ordering.domain.model.commands.CreateFuelRequestCommand;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/v2/buyer-companies/{companyId}/replenishment-requests",
        produces = MediaType.APPLICATION_JSON_VALUE)
public class ReplenishmentRequestsController {
    private final FuelRequestService service;

    public ReplenishmentRequestsController(FuelRequestService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("@currentUserAccess.ownsCompany(#companyId)")
    public ResponseEntity<FuelRequestResource> create(@PathVariable Long companyId,
                                                       @Valid @RequestBody CreateReplenishmentRequestResource resource) {
        try {
            var request = service.create(new CreateFuelRequestCommand(companyId, resource.providerId(),
                    resource.tankId(), resource.fuelProductId(), resource.quantity(), resource.unit(),
                    resource.deliveryAddress(), resource.deliveryDate(), "MANUAL"));
            return ResponseEntity.status(HttpStatus.CREATED).body(toResource(request));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/{requestId}/cancel")
    @PreAuthorize("@currentUserAccess.ownsCompany(#companyId)")
    public ResponseEntity<FuelRequestResource> cancel(@PathVariable Long companyId, @PathVariable Long requestId) {
        try {
            var request = service.findById(requestId).filter(item -> companyId.equals(item.buyerCompanyId()))
                    .orElseThrow(() -> new IllegalArgumentException("Request is outside buyer company"));
            return ResponseEntity.ok(toResource(service.cancel(request.id())));
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return ResponseEntity.badRequest().build();
        }
    }

    private static FuelRequestResource toResource(com.primefuel.fulltank.platform.ordering.application.ports.FuelRequestData r) {
        return new FuelRequestResource(r.id(), r.buyerCompanyId(), r.providerId(), r.equipmentId(),
                r.fuelProductId(), r.fuelType(), r.productName(), r.quantity(), r.unit(), r.unitPrice(),
                r.deliveryAddress(), r.deliveryDate(), r.status(), r.source(), r.rejectionReason(),
                r.createdAt(), r.updatedAt());
    }
}
