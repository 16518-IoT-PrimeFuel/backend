package com.primefuel.fulltank.platform.fulfillment.application.internal.commandservices;

import com.primefuel.fulltank.platform.fulfillment.api.DeliveryIntegration;
import com.primefuel.fulltank.platform.fulfillment.application.commandservices.DeliveryCommandService;
import com.primefuel.fulltank.platform.fulfillment.application.internal.commandservices.DeliveryLifecycleServiceImpl;
import com.primefuel.fulltank.platform.fulfillment.domain.model.aggregates.Delivery;
import com.primefuel.fulltank.platform.fulfillment.domain.model.commands.ArriveDeliveryCommand;
import com.primefuel.fulltank.platform.fulfillment.domain.model.commands.AssignDeliveryCommand;
import com.primefuel.fulltank.platform.fulfillment.domain.model.commands.CompleteDeliveryCommand;
import com.primefuel.fulltank.platform.fulfillment.domain.model.commands.CompletePhysicalDeliveryCommand;
import com.primefuel.fulltank.platform.fulfillment.domain.model.commands.CreateDeliveryCommand;
import com.primefuel.fulltank.platform.fulfillment.domain.model.commands.DispatchDeliveryCommand;
import com.primefuel.fulltank.platform.fulfillment.domain.model.commands.FailDeliveryCommand;
import com.primefuel.fulltank.platform.fulfillment.domain.model.commands.StartDeliveryCommand;
import com.primefuel.fulltank.platform.fulfillment.domain.model.valueobjects.DeliveryPhysicalState;
import com.primefuel.fulltank.platform.fulfillment.domain.repositories.DeliveryRepository;
import com.primefuel.fulltank.platform.ordering.application.queryservices.FuelOrderQueryService;
import com.primefuel.fulltank.platform.shared.application.result.ApplicationError;
import com.primefuel.fulltank.platform.shared.application.result.Result;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * T14-B/T15-B legacy delivery adapter. It keeps the v1 (S14) contract intact — same routes, same request
 * bodies, same legacy {@code status} in responses — while routing every delivery state mutation through the
 * physical machine ({@link DeliveryLifecycleServiceImpl}). Since T15-B it owns <strong>no foreign repository</strong>:
 * the cross-module orchestration of {@code create} and the legacy {@code complete} side effects live behind the
 * {@link DeliveryIntegration} port (implemented in {@code applicationflows}). The v1↔physical map is:
 *
 * <ul>
 *   <li>{@code POST /deliveries} (create) — the whole cross-module orchestration is delegated to
 *       {@link DeliveryIntegration#createDelivery}: an order with an accepted replenishment request is assigned
 *       through the exclusive-reservation orchestrator (race-safe, idempotent per order); a direct legacy order
 *       keeps its original side effects. Either way the delivery is {@code DISPATCHED} for v1 readers.</li>
 *   <li>{@code POST /deliveries/{id}/dispatch} → {@code AssignDeliveryCommand} (idempotent).</li>
 *   <li>{@code POST /deliveries/{id}/fail} → {@code FailDeliveryCommand}.</li>
 *   <li>{@code POST /deliveries/{id}/complete} → {@code CompletePhysicalDeliveryCommand}, then the legacy
 *       foreign side effects (release resources, refuel, settle order) via {@link DeliveryIntegration}.</li>
 * </ul>
 *
 * <p>The only remaining cross-module read is {@link FuelOrderQueryService} (the public ordering query surface,
 * not a repository) used to source the order's requested quantity as close evidence.
 */
@Service
public class DeliveryCommandServiceImpl implements DeliveryCommandService {

    private final DeliveryRepository deliveryRepository;
    private final DeliveryLifecycleServiceImpl deliveryLifecycleService;
    private final FuelOrderQueryService fuelOrderQueryService;
    private final DeliveryIntegration deliveryIntegration;

    public DeliveryCommandServiceImpl(DeliveryRepository deliveryRepository,
                                      DeliveryLifecycleServiceImpl deliveryLifecycleService,
                                      FuelOrderQueryService fuelOrderQueryService,
                                      DeliveryIntegration deliveryIntegration) {
        this.deliveryRepository = deliveryRepository;
        this.deliveryLifecycleService = deliveryLifecycleService;
        this.fuelOrderQueryService = fuelOrderQueryService;
        this.deliveryIntegration = deliveryIntegration;
    }

    /**
     * Creates the delivery for an order. Not transactional on purpose: the cross-module orchestration (and its
     * single transaction) lives in {@link DeliveryIntegration}, so a failure there rolls back cleanly instead
     * of committing a partially applied assignment.
     */
    @Override
    public Result<Delivery, ApplicationError> handle(CreateDeliveryCommand command) {
        var created = deliveryIntegration.createDelivery(new DeliveryIntegration.CreateLegacyDeliveryCommand(
                command.orderId(), command.providerId(), command.driverId(), command.vehicleId(),
                command.scheduledDate(), command.notes()));
        if (created.isFailure()) {
            return Result.failure(errorOf(created));
        }
        return deliveryRepository.findById(created.getOrElse(null))
                .<Result<Delivery, ApplicationError>>map(Result::success)
                .orElseGet(() -> Result.failure(
                        ApplicationError.notFound("Delivery", String.valueOf(created.getOrElse(null)))));
    }

    /** Legacy dispatch → materialise {@code ASSIGNED}; delegated so v1 shares the machine (and its journal). */
    @Override
    @Transactional
    public Result<Delivery, ApplicationError> handle(DispatchDeliveryCommand command) {
        return deliveryLifecycleService.handle(new AssignDeliveryCommand(command.deliveryId()));
    }

    @Override
    @Transactional
    public Result<Delivery, ApplicationError> handle(CompleteDeliveryCommand command) {
        var existing = deliveryRepository.findById(command.deliveryId());
        if (existing.isEmpty()) {
            return Result.failure(ApplicationError.notFound("Delivery", command.deliveryId().toString()));
        }
        var delivery = existing.get();
        // v1 has no volume input: the legacy close means "delivered in full", so the evidence is the order's
        // requested quantity (A2). Without it the physical close cannot satisfy U11 and is refused.
        var requestedVolume = fuelOrderQueryService.findRequestedQuantity(delivery.getOrderId());
        if (requestedVolume.isEmpty()) {
            return Result.failure(ApplicationError.businessRuleViolation("delivery.complete",
                    "The requested volume of the order could not be resolved; the physical close needs it as evidence"));
        }
        // v1 exposes no start/arrive: materialise the states it never recorded before closing (A1).
        var advanced = advanceToArrived(delivery);
        if (advanced.isFailure()) {
            return advanced;
        }
        var completed = deliveryLifecycleService.handle(
                new CompletePhysicalDeliveryCommand(command.deliveryId(), requestedVolume.get()));
        if (completed.isFailure()) {
            return completed;
        }
        // v1 foreign side effects (release resources, refuel, settle order), now owned by the composition root
        // behind the DeliveryIntegration port. They run only after the physical close succeeded, same transaction.
        deliveryIntegration.applyCompletionEffects(new DeliveryIntegration.CompletionEffectsCommand(
                delivery.getOrderId(), delivery.getDriverId(), delivery.getVehicleId(),
                delivery.getAssignmentCommandId()));
        return completed;
    }

    @Override
    @Transactional
    public Result<Delivery, ApplicationError> handle(FailDeliveryCommand command) {
        return deliveryLifecycleService.handle(new FailDeliveryCommand(command.deliveryId(), command.reason()));
    }

    /**
     * Collapses the shallow v1 lifecycle onto the deep machine: from {@code ASSIGNED} it starts and arrives
     * before the close, so the physical close is legal. A delivery already at {@code ARRIVED}/{@code DELIVERING}
     * is left untouched; a terminal one is not advanced and the close will answer 409.
     */
    private Result<Delivery, ApplicationError> advanceToArrived(Delivery delivery) {
        var state = delivery.currentPhysicalState();
        if (state == DeliveryPhysicalState.ASSIGNED) {
            var started = deliveryLifecycleService.handle(new StartDeliveryCommand(delivery.getId()));
            if (started.isFailure()) {
                return started;
            }
            state = DeliveryPhysicalState.STARTED;
        }
        if (state == DeliveryPhysicalState.STARTED) {
            var arrived = deliveryLifecycleService.handle(new ArriveDeliveryCommand(delivery.getId()));
            if (arrived.isFailure()) {
                return arrived;
            }
        }
        return Result.success(delivery);
    }

    private static <T> ApplicationError errorOf(Result<T, ApplicationError> failure) {
        return switch (failure) {
            case Result.Failure<T, ApplicationError> f -> f.error();
            case Result.Success<T, ApplicationError> ignored ->
                    throw new IllegalArgumentException("Expected a failed result");
        };
    }
}
