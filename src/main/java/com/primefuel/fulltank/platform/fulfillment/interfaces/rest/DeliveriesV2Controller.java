package com.primefuel.fulltank.platform.fulfillment.interfaces.rest;

import com.primefuel.fulltank.platform.fulfillment.application.commandservices.DeliveryLifecycleService;
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

    private final DeliveryLifecycleService deliveryLifecycleService;
    private final DeliveryQueryService deliveryQueryService;
    private final DeliveryStateTransitionRepository transitionRepository;
    private final TenantAccess tenantAccess;

    public DeliveriesV2Controller(DeliveryLifecycleService deliveryLifecycleService,
                                  DeliveryQueryService deliveryQueryService,
                                  DeliveryStateTransitionRepository transitionRepository,
                                  TenantAccess tenantAccess) {
        this.deliveryLifecycleService = deliveryLifecycleService;
        this.deliveryQueryService = deliveryQueryService;
        this.transitionRepository = transitionRepository;
        this.tenantAccess = tenantAccess;
    }

    @PostMapping("/{deliveryId}/assign")
    public ResponseEntity<?> assign(@PathVariable Long deliveryId) {
        return advance(deliveryId, () -> deliveryLifecycleService.handle(new AssignDeliveryCommand(deliveryId)));
    }

    @PostMapping("/{deliveryId}/start")
    public ResponseEntity<?> start(@PathVariable Long deliveryId) {
        return advance(deliveryId, () -> deliveryLifecycleService.handle(new StartDeliveryCommand(deliveryId)));
    }

    @PostMapping("/{deliveryId}/arrive")
    public ResponseEntity<?> arrive(@PathVariable Long deliveryId) {
        return advance(deliveryId, () -> deliveryLifecycleService.handle(new ArriveDeliveryCommand(deliveryId)));
    }

    @PostMapping("/{deliveryId}/complete")
    public ResponseEntity<?> complete(@PathVariable Long deliveryId,
                                      @RequestBody CompleteDeliveryResource resource) {
        return advance(deliveryId, () -> deliveryLifecycleService.handle(
                new CompletePhysicalDeliveryCommand(deliveryId, resource.deliveredVolume())));
    }

    @PostMapping("/{deliveryId}/fail")
    public ResponseEntity<?> fail(@PathVariable Long deliveryId,
                                  @RequestBody FailDeliveryResource resource) {
        return advance(deliveryId, () -> deliveryLifecycleService.handle(
                new FailDeliveryCommand(deliveryId, resource.reason())));
    }

    @PostMapping("/{deliveryId}/cancel")
    public ResponseEntity<?> cancel(@PathVariable Long deliveryId,
                                    @RequestBody FailDeliveryResource resource) {
        return advance(deliveryId, () -> deliveryLifecycleService.handle(
                new CancelDeliveryCommand(deliveryId, resource.reason())));
    }

    @GetMapping("/{deliveryId}")
    public ResponseEntity<DeliveryV2Resource> get(@PathVariable Long deliveryId) {
        if (!owns(deliveryId)) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        return deliveryQueryService.handle(new GetDeliveryByIdQuery(deliveryId))
                .map(delivery -> new ResponseEntity<>(toResource(delivery), HttpStatus.OK))
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

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
