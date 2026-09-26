package com.primefuel.fulltank.platform.fulfillment.interfaces.rest;

import com.primefuel.fulltank.platform.fulfillment.application.internal.commandservices.DeliveryLifecycleServiceImpl;
import com.primefuel.fulltank.platform.fulfillment.application.queryservices.DeliveryQueryService;
import com.primefuel.fulltank.platform.fulfillment.domain.model.aggregates.Delivery;
import com.primefuel.fulltank.platform.fulfillment.domain.model.commands.ArriveDeliveryCommand;
import com.primefuel.fulltank.platform.fulfillment.domain.model.commands.AssignDeliveryCommand;
import com.primefuel.fulltank.platform.fulfillment.domain.model.commands.CancelDeliveryCommand;
import com.primefuel.fulltank.platform.fulfillment.domain.model.commands.CompletePhysicalDeliveryCommand;
import com.primefuel.fulltank.platform.fulfillment.domain.model.commands.FailDeliveryCommand;
import com.primefuel.fulltank.platform.fulfillment.domain.model.commands.StartDeliveryCommand;
import com.primefuel.fulltank.platform.fulfillment.domain.model.queries.GetDeliveryByIdQuery;
import com.primefuel.fulltank.platform.fulfillment.domain.repositories.DeliveryStateTransitionRepository;
import com.primefuel.fulltank.platform.fulfillment.interfaces.rest.resources.CompleteDeliveryResource;
import com.primefuel.fulltank.platform.fulfillment.interfaces.rest.resources.DeliveryTransitionResource;
import com.primefuel.fulltank.platform.fulfillment.interfaces.rest.resources.DeliveryV2Resource;
import com.primefuel.fulltank.platform.fulfillment.interfaces.rest.resources.FailDeliveryResource;
import com.primefuel.fulltank.platform.iam.api.TenantAccess;
import com.primefuel.fulltank.platform.shared.application.result.ApplicationError;
import com.primefuel.fulltank.platform.shared.application.result.Result;
import com.primefuel.fulltank.platform.shared.interfaces.rest.transform.ResponseEntityAssembler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.function.Supplier;

/**
 * v2 physical delivery lifecycle (S14/T14-A): {@code assign|start|arrive|complete|fail} (+ {@code cancel}).
 * Only the provider that owns the delivery may advance it; a foreign delivery answers 404. An illegal
 * transition answers 409 (the machine rejects it), a bad evidence volume answers 400 and a
 * {@code complete} whose requested volume cannot be resolved answers 422.
 *
 * <p>The legacy v1 routes are untouched — T14-B is the ticket that reroutes them through this machine.
 */
@RestController
@RequestMapping(value = "/api/v2/deliveries", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Delivery lifecycle", description = "Physical delivery state machine (v2)")
public class DeliveriesV2Controller {

    private final DeliveryLifecycleServiceImpl deliveryLifecycleService;
    private final DeliveryQueryService deliveryQueryService;
    private final DeliveryStateTransitionRepository transitionRepository;
    private final TenantAccess tenantAccess;

    public DeliveriesV2Controller(DeliveryLifecycleServiceImpl deliveryLifecycleService,
                                  DeliveryQueryService deliveryQueryService,
                                  DeliveryStateTransitionRepository transitionRepository,
                                  TenantAccess tenantAccess) {
        this.deliveryLifecycleService = deliveryLifecycleService;
        this.deliveryQueryService = deliveryQueryService;
        this.transitionRepository = transitionRepository;
        this.tenantAccess = tenantAccess;
    }

    /**
     * Assigns a delivery to its driver/vehicle.
     *
     * <p>Tenant-scoped: a delivery the caller's provider tenant does not own is reported as not found.
     * Assigning an already-assigned delivery is idempotent and produces no duplicate journal row or
     * event.</p>
     */
    @Operation(summary = "Assign a delivery",
            description = "Materialises the assigned state of a delivery owned by the caller's provider tenant; idempotent when already assigned.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Delivery assigned (or already assigned)."),
            @ApiResponse(responseCode = "404", description = "Delivery does not exist or is not owned by the caller's provider tenant."),
            @ApiResponse(responseCode = "409", description = "The delivery cannot be assigned from its current physical state.")
    })
    @PostMapping("/{deliveryId}/assign")
    public ResponseEntity<?> assign(@PathVariable Long deliveryId) {
        return advance(deliveryId, () -> deliveryLifecycleService.handle(new AssignDeliveryCommand(deliveryId)));
    }

    /**
     * Starts a delivery (in route).
     *
     * <p>Tenant-scoped. The machine only accepts a start from the assigned state.</p>
     */
    @Operation(summary = "Start a delivery",
            description = "Moves a delivery owned by the caller's provider tenant to the started state.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Delivery started."),
            @ApiResponse(responseCode = "404", description = "Delivery does not exist or is not owned by the caller's provider tenant."),
            @ApiResponse(responseCode = "409", description = "The delivery cannot be started from its current physical state.")
    })
    @PostMapping("/{deliveryId}/start")
    public ResponseEntity<?> start(@PathVariable Long deliveryId) {
        return advance(deliveryId, () -> deliveryLifecycleService.handle(new StartDeliveryCommand(deliveryId)));
    }

    /**
     * Marks a delivery as arrived.
     *
     * <p>Tenant-scoped. The machine only accepts an arrival from the started state.</p>
     */
    @Operation(summary = "Mark a delivery as arrived",
            description = "Moves a delivery owned by the caller's provider tenant to the arrived state.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Delivery marked as arrived."),
            @ApiResponse(responseCode = "404", description = "Delivery does not exist or is not owned by the caller's provider tenant."),
            @ApiResponse(responseCode = "409", description = "The delivery cannot arrive from its current physical state.")
    })
    @PostMapping("/{deliveryId}/arrive")
    public ResponseEntity<?> arrive(@PathVariable Long deliveryId) {
        return advance(deliveryId, () -> deliveryLifecycleService.handle(new ArriveDeliveryCommand(deliveryId)));
    }

    /**
     * Completes a delivery with the delivered volume as evidence.
     *
     * <p>Tenant-scoped. U11 keeps both the requested and delivered volumes; the delivered volume must be
     * a valid quantity (otherwise 400) and the order's requested volume must be resolvable to record the
     * evidence (otherwise 422). A close from the arrived state also journals the implicit discharge, so
     * the whole operation stays in one transaction.</p>
     */
    @Operation(summary = "Complete a delivery",
            description = "Closes a delivery owned by the caller's provider tenant, recording the delivered volume as evidence.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Delivery completed."),
            @ApiResponse(responseCode = "400", description = "The delivered volume is not a valid evidence quantity."),
            @ApiResponse(responseCode = "404", description = "Delivery does not exist or is not owned by the caller's provider tenant."),
            @ApiResponse(responseCode = "409", description = "The delivery cannot be completed from its current physical state, or it was advanced concurrently."),
            @ApiResponse(responseCode = "422", description = "The requested volume of the order could not be resolved to serve as close evidence.")
    })
    @PostMapping("/{deliveryId}/complete")
    public ResponseEntity<?> complete(@PathVariable Long deliveryId,
                                      @RequestBody CompleteDeliveryResource resource) {
        return advance(deliveryId, () -> deliveryLifecycleService.handle(
                new CompletePhysicalDeliveryCommand(deliveryId, resource.deliveredVolume())));
    }

    /**
     * Fails a delivery.
     *
     * <p>Tenant-scoped. Failing is a terminal outcome and requires a reason.</p>
     */
    @Operation(summary = "Fail a delivery",
            description = "Moves a delivery owned by the caller's provider tenant to the failed terminal state, recording a reason.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Delivery failed."),
            @ApiResponse(responseCode = "404", description = "Delivery does not exist or is not owned by the caller's provider tenant."),
            @ApiResponse(responseCode = "409", description = "The delivery cannot be failed from its current physical state, or it was advanced concurrently.")
    })
    @PostMapping("/{deliveryId}/fail")
    public ResponseEntity<?> fail(@PathVariable Long deliveryId,
                                  @RequestBody FailDeliveryResource resource) {
        return advance(deliveryId, () -> deliveryLifecycleService.handle(
                new FailDeliveryCommand(deliveryId, resource.reason())));
    }

    /**
     * Cancels a delivery.
     *
     * <p>Tenant-scoped. Cancelling is a terminal outcome distinct from failure and requires a reason.</p>
     */
    @Operation(summary = "Cancel a delivery",
            description = "Moves a delivery owned by the caller's provider tenant to the cancelled terminal state, recording a reason.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Delivery cancelled."),
            @ApiResponse(responseCode = "404", description = "Delivery does not exist or is not owned by the caller's provider tenant."),
            @ApiResponse(responseCode = "409", description = "The delivery cannot be cancelled from its current physical state, or it was advanced concurrently.")
    })
    @PostMapping("/{deliveryId}/cancel")
    public ResponseEntity<?> cancel(@PathVariable Long deliveryId,
                                    @RequestBody FailDeliveryResource resource) {
        return advance(deliveryId, () -> deliveryLifecycleService.handle(
                new CancelDeliveryCommand(deliveryId, resource.reason())));
    }

    /**
     * Retrieves a single delivery with its physical state.
     *
     * <p>Tenant-scoped to the caller's provider; a foreign delivery is reported as not found.</p>
     */
    @Operation(summary = "Get a delivery",
            description = "Returns the delivery (with both legacy status and physical state) owned by the caller's provider tenant.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Delivery returned."),
            @ApiResponse(responseCode = "404", description = "Delivery does not exist or is not owned by the caller's provider tenant.")
    })
    @GetMapping("/{deliveryId}")
    public ResponseEntity<DeliveryV2Resource> get(@PathVariable Long deliveryId) {
        if (!owns(deliveryId)) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        return deliveryQueryService.handle(new GetDeliveryByIdQuery(deliveryId))
                .map(delivery -> new ResponseEntity<>(toResource(delivery), HttpStatus.OK))
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    /**
     * Lists the append-only state journal of a delivery.
     *
     * <p>Tenant-scoped to the caller's provider. Each row records a physical transition, the aggregate
     * version it happened at and its timestamp, which is what makes the history reconstructible.</p>
     */
    @Operation(summary = "List a delivery's state transitions",
            description = "Returns the append-only journal of physical state transitions of a delivery owned by the caller's provider tenant.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Transitions returned."),
            @ApiResponse(responseCode = "404", description = "Delivery does not exist or is not owned by the caller's provider tenant.")
    })
    @GetMapping("/{deliveryId}/transitions")
    public ResponseEntity<List<DeliveryTransitionResource>> transitions(@PathVariable Long deliveryId) {
        if (!owns(deliveryId)) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        var resources = transitionRepository.findByDeliveryId(deliveryId).stream()
                .map(transition -> new DeliveryTransitionResource(transition.id(), transition.fromState(),
                        transition.toState(), transition.aggregateVersion(), transition.occurredAt()))
                .toList();
        return new ResponseEntity<>(resources, HttpStatus.OK);
    }

    private ResponseEntity<?> advance(Long deliveryId, Supplier<Result<Delivery, ApplicationError>> action) {
        if (!owns(deliveryId)) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        return ResponseEntityAssembler.toResponseEntityFromResult(
                action.get(), DeliveriesV2Controller::toResource, HttpStatus.OK);
    }

    private boolean owns(Long deliveryId) {
        var providerId = tenantAccess.currentProviderId();
        return providerId.isPresent()
                && deliveryQueryService.handle(new GetDeliveryByIdQuery(deliveryId))
                .map(delivery -> providerId.get().equals(delivery.getProviderId()))
                .orElse(false);
    }

    private static DeliveryV2Resource toResource(Delivery delivery) {
        return new DeliveryV2Resource(delivery.getId(), delivery.getOrderId(), delivery.getProviderId(),
                delivery.getDriverId(), delivery.getVehicleId(), delivery.getStatus(),
                delivery.currentPhysicalState(), delivery.getRequestedVolume(), delivery.getDeliveredVolume(),
                delivery.getDispatchedAt(), delivery.getStartedAt(), delivery.getArrivedAt(),
                delivery.getDeliveringAt(), delivery.getDeliveredAt(), delivery.getNotes(),
                delivery.getVersion());
    }
}
