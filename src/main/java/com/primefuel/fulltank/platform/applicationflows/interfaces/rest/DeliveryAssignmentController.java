package com.primefuel.fulltank.platform.applicationflows.interfaces.rest;

import com.primefuel.fulltank.platform.applicationflows.AssignDeliveryFlow;
import com.primefuel.fulltank.platform.applicationflows.AssignDeliveryFlowCommand;
import com.primefuel.fulltank.platform.applicationflows.AssignDeliveryResult;
import com.primefuel.fulltank.platform.applicationflows.interfaces.rest.resources.AssignDeliveryResource;
import com.primefuel.fulltank.platform.applicationflows.interfaces.rest.resources.AssignmentResource;
import com.primefuel.fulltank.platform.iam.api.TenantAccess;
import com.primefuel.fulltank.platform.shared.interfaces.rest.transform.ResponseEntityAssembler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * v2 assignment (S15/T15-A): {@code POST /api/v2/deliveries}. Unlike the legacy create (which decremented
 * stock, assigned and dispatched in one go, straight against foreign repositories), this endpoint delegates
 * to {@link AssignDeliveryFlow}, which orchestrates acceptance consumption plus the supply and fleet
 * reservations in a single transaction.
 *
 * <p>Tenant-safe: the provider is resolved from the caller's principal, never from the body. The order must
 * be backed by an <em>accepted</em> replenishment request owned by that provider, otherwise the assignment
 * cannot start (this is what stops a legacy order without acceptance from entering here). A retry with the
 * same {@code commandId} returns the same delivery.
 *
 * <p>This controller lives in {@code applicationflows} (the composition root) so the {@code fulfillment}
 * module does not have to depend on the orchestrator; it stays a thin HTTP adapter over the flow.
 */
@RestController
@RequestMapping(value = "/api/v2/deliveries", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Delivery assignment", description = "Transactional assignment after acceptance (v2)")
public class DeliveryAssignmentController {

    private final AssignDeliveryFlow assignDeliveryFlow;
    private final TenantAccess tenantAccess;

    public DeliveryAssignmentController(AssignDeliveryFlow assignDeliveryFlow, TenantAccess tenantAccess) {
        this.assignDeliveryFlow = assignDeliveryFlow;
        this.tenantAccess = tenantAccess;
    }

    @Operation(summary = "Assign a delivery for an accepted order",
            description = "Consumes the order's replenishment acceptance, reserves supply and fleet, and creates the assigned delivery in one transaction; idempotent per commandId.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Delivery assigned (or the existing delivery for the commandId returned)."),
            @ApiResponse(responseCode = "400", description = "The command is missing its commandId/orderId or the window/volume is invalid."),
            @ApiResponse(responseCode = "403", description = "Caller is not authenticated as a provider tenant."),
            @ApiResponse(responseCode = "404", description = "No replenishment request accepted by the caller's provider backs the order."),
            @ApiResponse(responseCode = "409", description = "The request is not accepted, its acceptance was already consumed, or the supply/fleet resource is unavailable.")
    })
    @PostMapping
    public ResponseEntity<?> assign(@RequestBody AssignDeliveryResource resource) {
        var providerId = tenantAccess.currentProviderId();
        if (providerId.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }
        var command = new AssignDeliveryFlowCommand(resource.commandId(), resource.orderId(), providerId.get(),
                resource.driverId(), resource.tankerId(), resource.windowStart(), resource.windowEnd(),
                resource.scheduledDate(), resource.notes());
        var result = assignDeliveryFlow.assign(command);
        return ResponseEntityAssembler.toResponseEntityFromResult(
                result, DeliveryAssignmentController::toResource, HttpStatus.CREATED);
    }

    private static AssignmentResource toResource(AssignDeliveryResult result) {
        return new AssignmentResource(result.deliveryId(), result.orderId(), result.providerId(),
                result.driverId(), result.tankerId(), result.supplyReservationId(), result.fleetReservationId(),
                result.physicalState(), result.commandId());
    }
}
