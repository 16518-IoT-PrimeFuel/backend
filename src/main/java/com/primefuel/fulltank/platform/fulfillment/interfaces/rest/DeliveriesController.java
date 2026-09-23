package com.primefuel.fulltank.platform.fulfillment.interfaces.rest;

import com.primefuel.fulltank.platform.fulfillment.application.commandservices.DeliveryCommandService;
import com.primefuel.fulltank.platform.fulfillment.application.queryservices.DeliveryQueryService;
import com.primefuel.fulltank.platform.fulfillment.domain.model.commands.CompleteDeliveryCommand;
import com.primefuel.fulltank.platform.fulfillment.domain.model.commands.DispatchDeliveryCommand;
import com.primefuel.fulltank.platform.fulfillment.domain.model.commands.FailDeliveryCommand;
import com.primefuel.fulltank.platform.fulfillment.domain.model.queries.GetAllDeliveriesQuery;
import com.primefuel.fulltank.platform.fulfillment.domain.model.queries.GetDeliveryByIdQuery;
import com.primefuel.fulltank.platform.fulfillment.domain.model.queries.GetDeliveryByOrderIdQuery;
import com.primefuel.fulltank.platform.fulfillment.interfaces.rest.resources.CreateDeliveryResource;
import com.primefuel.fulltank.platform.fulfillment.interfaces.rest.resources.DeliveryResource;
import com.primefuel.fulltank.platform.fulfillment.interfaces.rest.resources.FailDeliveryResource;
import com.primefuel.fulltank.platform.fulfillment.interfaces.rest.transform.CreateDeliveryCommandFromResourceAssembler;
import com.primefuel.fulltank.platform.fulfillment.interfaces.rest.transform.DeliveryResourceFromEntityAssembler;
import com.primefuel.fulltank.platform.iam.api.TenantAccess;
import com.primefuel.fulltank.platform.ordering.application.queryservices.FuelOrderQueryService;
import com.primefuel.fulltank.platform.ordering.domain.model.queries.GetFuelOrderByIdQuery;
import com.primefuel.fulltank.platform.shared.interfaces.rest.transform.ResponseEntityAssembler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;

@RestController
@RequestMapping(value = "/api/v1/deliveries", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Deliveries", description = "Fulfillment management endpoints")
public class DeliveriesController {

    private final DeliveryCommandService deliveryCommandService;
    private final DeliveryQueryService deliveryQueryService;
    private final FuelOrderQueryService fuelOrderQueryService;
    private final TenantAccess tenantAccess;

    public DeliveriesController(DeliveryCommandService deliveryCommandService,
                                DeliveryQueryService deliveryQueryService,
                                FuelOrderQueryService fuelOrderQueryService,
                                TenantAccess tenantAccess) {
        this.deliveryCommandService = deliveryCommandService;
        this.deliveryQueryService = deliveryQueryService;
        this.fuelOrderQueryService = fuelOrderQueryService;
        this.tenantAccess = tenantAccess;
    }

    /**
     * Creates a delivery for an order on behalf of the caller's provider tenant.
     *
     * <p>The provider in the body must be the caller's own. The driver, the vehicle and the order must
     * all belong to that provider, the driver/vehicle must be available, the vehicle capacity must be
     * sufficient and the product must have enough stock; several of those preconditions answer 409.
     * Creating the delivery assigns the driver, routes the vehicle, decrements stock and dispatches the
     * order, all in one transaction.</p>
     */
    @Operation(summary = "Create a delivery",
            description = "Creates a delivery for a provider order after validating fleet availability, vehicle capacity, stock and ownership.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Delivery created and order dispatched."),
            @ApiResponse(responseCode = "403", description = "Caller does not own the provider in the request body."),
            @ApiResponse(responseCode = "404", description = "The driver/vehicle or the fuel order could not be found for the provider."),
            @ApiResponse(responseCode = "409", description = "Driver/vehicle not available or not owned by the provider, insufficient capacity or stock, or a delivery already exists for the order.")
    })
    @PostMapping
    @PreAuthorize("@tenantAccess.ownsProvider(#resource.providerId())")
    public ResponseEntity<?> createDelivery(@RequestBody CreateDeliveryResource resource) {
        var command = CreateDeliveryCommandFromResourceAssembler.toCommandFromResource(resource);
        var result = deliveryCommandService.handle(command);
        return ResponseEntityAssembler.toResponseEntityFromResult(
                result,
                DeliveryResourceFromEntityAssembler::toResourceFromEntity,
                HttpStatus.CREATED);
    }

    /**
     * Dispatches a delivery.
     *
     * <p>Only the owning provider tenant may advance it; a foreign or missing delivery is reported as not
     * found. Dispatch is routed through the physical machine and is idempotent for an already-assigned
     * delivery.</p>
     */
    @Operation(summary = "Dispatch a delivery",
            description = "Moves a delivery to the assigned/dispatched state on behalf of its owning provider tenant.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Delivery dispatched."),
            @ApiResponse(responseCode = "404", description = "Delivery does not exist or is not owned by the caller."),
            @ApiResponse(responseCode = "409", description = "The delivery cannot be dispatched from its current physical state.")
    })
    @PostMapping("/{deliveryId}/dispatch")
    public ResponseEntity<?> dispatchDelivery(@PathVariable Long deliveryId) {
        if (!ownsDeliveryAsProvider(deliveryId)) return ResponseEntity.notFound().build();
        var result = deliveryCommandService.handle(new DispatchDeliveryCommand(deliveryId));
        return ResponseEntityAssembler.toResponseEntityFromResult(
                result,
                DeliveryResourceFromEntityAssembler::toResourceFromEntity,
                HttpStatus.OK);
    }

    /**
     * Completes a delivery.
     *
     * <p>Only the owning provider tenant may close it. The v1 close carries no delivered volume, so the
     * adapter uses the order's requested quantity as evidence and materialises the intermediate states
     * v1 never recorded before closing; this keeps the legacy contract while the physical machine still
     * enforces its invariant.</p>
     */
    @Operation(summary = "Complete a delivery",
            description = "Closes a delivery on behalf of its owning provider tenant, using the order's requested quantity as delivered evidence.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Delivery completed."),
            @ApiResponse(responseCode = "404", description = "Delivery does not exist or is not owned by the caller."),
            @ApiResponse(responseCode = "409", description = "The delivery cannot be completed from its current physical state."),
            @ApiResponse(responseCode = "422", description = "The requested volume of the order could not be resolved to serve as close evidence.")
    })
    @PostMapping("/{deliveryId}/complete")
    public ResponseEntity<?> completeDelivery(@PathVariable Long deliveryId) {
        if (!ownsDeliveryAsProvider(deliveryId)) return ResponseEntity.notFound().build();
        var result = deliveryCommandService.handle(new CompleteDeliveryCommand(deliveryId));
        return ResponseEntityAssembler.toResponseEntityFromResult(
                result,
                DeliveryResourceFromEntityAssembler::toResourceFromEntity,
                HttpStatus.OK);
    }

    /**
     * Fails a delivery.
     *
     * <p>Only the owning provider tenant may fail it; the reason is recorded. Failure is routed through
     * the physical machine.</p>
     */
    @Operation(summary = "Fail a delivery",
            description = "Moves a delivery to the failed state on behalf of its owning provider tenant, recording a reason.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Delivery failed."),
            @ApiResponse(responseCode = "404", description = "Delivery does not exist or is not owned by the caller."),
            @ApiResponse(responseCode = "409", description = "The delivery cannot be failed from its current physical state.")
    })
    @PostMapping("/{deliveryId}/fail")
    public ResponseEntity<?> failDelivery(@PathVariable Long deliveryId,
                                          @RequestBody FailDeliveryResource resource) {
        if (!ownsDeliveryAsProvider(deliveryId)) return ResponseEntity.notFound().build();
        var result = deliveryCommandService.handle(new FailDeliveryCommand(deliveryId, resource.reason()));
        return ResponseEntityAssembler.toResponseEntityFromResult(
                result,
                DeliveryResourceFromEntityAssembler::toResourceFromEntity,
                HttpStatus.OK);
    }

    /**
     * Lists every delivery in the platform.
     *
     * <p>Administrative endpoint; restricted to callers holding the ROLE_ADMIN authority.</p>
     */
    @Operation(summary = "List all deliveries",
            description = "Returns every registered delivery. Restricted to administrators.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Deliveries returned."),
            @ApiResponse(responseCode = "403", description = "Caller does not hold the ROLE_ADMIN authority.")
    })
    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<List<DeliveryResource>> getAllDeliveries() {
        var deliveries = deliveryQueryService.handle(new GetAllDeliveriesQuery());
        var resources = deliveries.stream().map(DeliveryResourceFromEntityAssembler::toResourceFromEntity).toList();
        return new ResponseEntity<>(resources, HttpStatus.OK);
    }

    /**
     * Lists the deliveries of a provider tenant.
     *
     * <p>Only the owning provider tenant may list them.</p>
     */
    @Operation(summary = "List deliveries by provider",
            description = "Returns the deliveries of the given provider tenant when it matches the caller's own provider.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Deliveries returned."),
            @ApiResponse(responseCode = "403", description = "Caller does not own the requested provider tenant.")
    })
    @GetMapping("/provider/{providerId}")
    @PreAuthorize("@tenantAccess.ownsProvider(#providerId)")
    public ResponseEntity<List<DeliveryResource>> getDeliveriesByProvider(@PathVariable Long providerId) {
        var resources = deliveryQueryService.handle(new GetAllDeliveriesQuery()).stream()
                .filter(delivery -> providerId.equals(delivery.getProviderId()))
                .map(DeliveryResourceFromEntityAssembler::toResourceFromEntity)
                .toList();
        return ResponseEntity.ok(resources);
    }

    /**
     * Retrieves a single delivery.
     *
     * <p>Readable by the delivery's provider tenant or by the buyer company of its order; anything else
     * is reported as not found.</p>
     */
    @Operation(summary = "Get a delivery by id",
            description = "Returns the delivery identified by the path id when the caller is its provider tenant or its order's buyer company.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Delivery returned."),
            @ApiResponse(responseCode = "404", description = "Delivery does not exist or is not visible to the caller.")
    })
    @GetMapping("/{deliveryId}")
    public ResponseEntity<DeliveryResource> getDeliveryById(@PathVariable Long deliveryId) {
        var result = deliveryQueryService.handle(new GetDeliveryByIdQuery(deliveryId))
                .filter(this::ownsDelivery);
        return result.map(d -> new ResponseEntity<>(
                        DeliveryResourceFromEntityAssembler.toResourceFromEntity(d), HttpStatus.OK))
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    /**
     * Retrieves the delivery of an order.
     *
     * <p>Readable by the delivery's provider tenant or by the buyer company of the order; anything else
     * is reported as not found.</p>
     */
    @Operation(summary = "Get the delivery of an order",
            description = "Returns the delivery attached to the given order when the caller is its provider tenant or the order's buyer company.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Delivery returned."),
            @ApiResponse(responseCode = "404", description = "No delivery exists for the order or it is not visible to the caller.")
    })
    @GetMapping("/order/{orderId}")
    public ResponseEntity<DeliveryResource> getDeliveryByOrder(@PathVariable Long orderId) {
        var result = deliveryQueryService.handle(new GetDeliveryByOrderIdQuery(orderId));
        return result.filter(this::ownsDelivery).map(d -> new ResponseEntity<>(
                        DeliveryResourceFromEntityAssembler.toResourceFromEntity(d), HttpStatus.OK))
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    private boolean ownsDeliveryAsProvider(Long deliveryId) {
        return deliveryQueryService.handle(new GetDeliveryByIdQuery(deliveryId))
                .filter(delivery -> tenantAccess.ownsProvider(delivery.getProviderId()))
                .isPresent();
    }

    private boolean ownsDelivery(com.primefuel.fulltank.platform.fulfillment.domain.model.aggregates.Delivery delivery) {
        if (tenantAccess.ownsProvider(delivery.getProviderId())) return true;
        return fuelOrderQueryService.handle(new GetFuelOrderByIdQuery(delivery.getOrderId()))
                .filter(order -> tenantAccess.ownsCompany(order.getCompanyId()))
                .isPresent();
    }
}
