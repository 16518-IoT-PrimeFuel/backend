package com.primefuel.fulltank.platform.ordering.interfaces.rest;

import com.primefuel.fulltank.platform.ordering.application.internal.commandservices.FuelRequestService;
import com.primefuel.fulltank.platform.ordering.application.internal.commandservices.LegacyFuelRequestBridge;
import com.primefuel.fulltank.platform.ordering.infrastructure.persistence.jpa.entities.FuelRequestPersistenceEntity;
import com.primefuel.fulltank.platform.ordering.interfaces.rest.resources.*;
import com.primefuel.fulltank.platform.ordering.interfaces.rest.transform.FuelOrderResourceFromEntityAssembler;
import com.primefuel.fulltank.platform.iam.infrastructure.authorization.sfs.services.CurrentUserAccess;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;

@RestController
@RequestMapping("/api/v1/fuel-requests")
public class FuelRequestsController {
    private final FuelRequestService service;
    private final CurrentUserAccess currentUserAccess;

    /** Field-injected so the existing (frozen) controller constructor is left untouched. */
    @Autowired
    private LegacyFuelRequestBridge bridge;

    public FuelRequestsController(FuelRequestService service, CurrentUserAccess currentUserAccess) {
        this.service = service;
        this.currentUserAccess = currentUserAccess;
    }

    /**
     * Creates a fuel request for the caller's buyer company.
     *
     * <p>Creation is routed through the T10-B bridge: it also opens a correlated replenishment request
     * so the review lifecycle is driven by the {@code replenishment} module while the legacy request id
     * is preserved. The fuel product must belong to the requested provider.</p>
     */
    @Operation(summary = "Create a fuel request",
            description = "Creates a legacy fuel request (and its linked replenishment review) for the caller's buyer company.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Fuel request created."),
            @ApiResponse(responseCode = "400", description = "The referenced fuel product does not exist or does not belong to the requested provider."),
            @ApiResponse(responseCode = "403", description = "Caller does not own the buyer company in the request body.")
    })
    @PostMapping
    @PreAuthorize("@currentUserAccess.ownsCompany(#resource.buyerCompanyId())")
    public ResponseEntity<FuelRequestResource> create(@RequestBody CreateFuelRequestResource resource) {
        return new ResponseEntity<>(toResource(bridge.create(resource)), HttpStatus.CREATED);
    }

    /**
     * Lists fuel requests filtered by buyer company or provider tenant.
     *
     * <p>Exactly one of the two filters is required and must be owned by the caller; supplying both, or
     * neither, is rejected.</p>
     */
    @Operation(summary = "List fuel requests",
            description = "Returns fuel requests for a single owned dimension: either the caller's buyer company or its provider tenant.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Fuel requests returned."),
            @ApiResponse(responseCode = "400", description = "Both buyerCompanyId and providerId were supplied."),
            @ApiResponse(responseCode = "403", description = "Neither filter was supplied, or the supplied filter is not owned by the caller.")
    })
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

    /**
     * Retrieves a single fuel request.
     *
     * <p>Readable by the buyer company or the provider tenant of the request; anything else is reported
     * as not found.</p>
     */
    @Operation(summary = "Get a fuel request by id",
            description = "Returns the fuel request identified by the path id when the caller is its buyer company or provider tenant.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Fuel request returned."),
            @ApiResponse(responseCode = "404", description = "Fuel request does not exist or is not owned by the caller.")
    })
    @GetMapping("/{requestId}")
    public ResponseEntity<FuelRequestResource> findById(@PathVariable Long requestId) {
        return service.findById(requestId)
                .filter(request -> currentUserAccess.ownsCompanyOrProvider(
                        request.getBuyerCompanyId(), request.getProviderId()))
                .map(request -> ResponseEntity.ok(toResource(request)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Accepts a pending fuel request.
     *
     * <p>Only the addressed provider tenant may accept it. Acceptance consumes the linked replenishment
     * review exactly once and then materialises the fuel order; a repeat acceptance fails.</p>
     */
    @Operation(summary = "Accept a fuel request",
            description = "Accepts a pending fuel request on behalf of the addressed provider tenant, creating the resulting fuel order.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Fuel request accepted and fuel order created."),
            @ApiResponse(responseCode = "400", description = "The request or its fuel product could not be found while materialising the order."),
            @ApiResponse(responseCode = "404", description = "Fuel request does not exist or is not addressed to the caller's provider tenant."),
            @ApiResponse(responseCode = "500", description = "The request was already accepted or the linked review could not be accepted.")
    })
    @PostMapping("/{requestId}/accept")
    public ResponseEntity<?> accept(@PathVariable Long requestId) {
        if (!ownsRequestAsProvider(requestId)) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(FuelOrderResourceFromEntityAssembler.toResourceFromEntity(bridge.accept(requestId)));
    }

    /**
     * Rejects a pending fuel request.
     *
     * <p>Only the addressed provider tenant may reject it; the reason is required and is propagated to
     * the linked replenishment review.</p>
     */
    @Operation(summary = "Reject a fuel request",
            description = "Rejects a pending fuel request on behalf of the addressed provider tenant, recording a reason.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Fuel request rejected."),
            @ApiResponse(responseCode = "400", description = "The rejection reason is missing or the request could not be found."),
            @ApiResponse(responseCode = "404", description = "Fuel request does not exist or is not addressed to the caller's provider tenant."),
            @ApiResponse(responseCode = "500", description = "The request is not pending and cannot be rejected.")
    })
    @PostMapping("/{requestId}/reject")
    public ResponseEntity<FuelRequestResource> reject(@PathVariable Long requestId,
                                                      @RequestBody RejectFuelRequestResource resource) {
        if (!ownsRequestAsProvider(requestId)) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(toResource(bridge.reject(requestId, resource.reason())));
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
