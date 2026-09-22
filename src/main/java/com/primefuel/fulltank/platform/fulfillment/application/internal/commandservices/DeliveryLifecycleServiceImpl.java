package com.primefuel.fulltank.platform.fulfillment.application.internal.commandservices;

import com.primefuel.fulltank.platform.fulfillment.api.events.DeliveryArrived;
import com.primefuel.fulltank.platform.fulfillment.api.events.DeliveryAssigned;
import com.primefuel.fulltank.platform.fulfillment.api.events.DeliveryCompleted;
import com.primefuel.fulltank.platform.fulfillment.api.events.DeliveryFailed;
import com.primefuel.fulltank.platform.fulfillment.api.events.DeliveryStarted;
import com.primefuel.fulltank.platform.fulfillment.application.commandservices.DeliveryLifecycleService;
import com.primefuel.fulltank.platform.fulfillment.domain.model.aggregates.Delivery;
import com.primefuel.fulltank.platform.fulfillment.domain.model.commands.ArriveDeliveryCommand;
import com.primefuel.fulltank.platform.fulfillment.domain.model.commands.AssignDeliveryCommand;
import com.primefuel.fulltank.platform.fulfillment.domain.model.commands.CancelDeliveryCommand;
import com.primefuel.fulltank.platform.fulfillment.domain.model.commands.CompletePhysicalDeliveryCommand;
import com.primefuel.fulltank.platform.fulfillment.domain.model.commands.FailDeliveryCommand;
import com.primefuel.fulltank.platform.fulfillment.domain.model.commands.StartDeliveryCommand;
import com.primefuel.fulltank.platform.fulfillment.domain.model.entities.DeliveryStateTransition;
import com.primefuel.fulltank.platform.fulfillment.domain.model.valueobjects.DeliveryPhysicalState;
import com.primefuel.fulltank.platform.fulfillment.domain.repositories.DeliveryRepository;
import com.primefuel.fulltank.platform.fulfillment.domain.repositories.DeliveryStateTransitionRepository;
import com.primefuel.fulltank.platform.ordering.application.queryservices.FuelOrderQueryService;
import com.primefuel.fulltank.platform.shared.application.result.ApplicationError;
import com.primefuel.fulltank.platform.shared.application.result.Result;
import com.primefuel.fulltank.platform.shared.events.EventPublicationRegistry;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.function.BiFunction;

/**
 * Advances the physical delivery machine. Each accepted command writes the delivery (optimistic-locked via
 * {@code version}), appends its journal row(s) and publishes one event through the T19-A outbox — all inside
 * the same transaction, so a rollback leaves neither state nor event behind. A close from {@code ARRIVED}
 * persists the implicit discharge as its own transition, so it journals two rows with their own versions.
 *
 * <p>Payment is not touched here: `PAID`/`PENDING_PAYMENT` are a commercial concern and never drive (or are
 * driven by) the physical state.
 */
@Service
public class DeliveryLifecycleServiceImpl implements DeliveryLifecycleService {

    private static final String AGGREGATE_TYPE = "Delivery";

    private final DeliveryRepository deliveryRepository;
    private final DeliveryStateTransitionRepository transitionRepository;
    private final FuelOrderQueryService fuelOrderQueryService;
    private final EventPublicationRegistry publicationRegistry;

    public DeliveryLifecycleServiceImpl(DeliveryRepository deliveryRepository,
                                        DeliveryStateTransitionRepository transitionRepository,
                                        FuelOrderQueryService fuelOrderQueryService,
                                        EventPublicationRegistry publicationRegistry) {
        this.deliveryRepository = deliveryRepository;
        this.transitionRepository = transitionRepository;
        this.fuelOrderQueryService = fuelOrderQueryService;
        this.publicationRegistry = publicationRegistry;
    }

    @Override
    @Transactional
    public Result<Delivery, ApplicationError> handle(AssignDeliveryCommand command) {
        var existing = deliveryRepository.findById(command.deliveryId());
        if (existing.isEmpty()) {
            return notFound(command.deliveryId());
        }
        var delivery = existing.get();
        boolean newlyAssigned;
        try {
            newlyAssigned = delivery.assign();
        } catch (IllegalStateException exception) {
            return Result.failure(ApplicationError.conflict("Delivery", exception.getMessage()));
        }
        if (!newlyAssigned) {
            // Idempotent: it was already assigned. No write, no duplicate event.
            return Result.success(delivery);
        }
        return commit(delivery, null, "delivery.assigned.v1",
                (saved, occurredAt) -> new DeliveryAssigned(saved.getId(), saved.getOrderId(),
                        saved.getProviderId(), saved.currentPhysicalState().name(), occurredAt).toPayloadJson());
    }

    @Override
    @Transactional
    public Result<Delivery, ApplicationError> handle(StartDeliveryCommand command) {
        var existing = deliveryRepository.findById(command.deliveryId());
        if (existing.isEmpty()) {
            return notFound(command.deliveryId());
        }
        var delivery = existing.get();
        var from = delivery.currentPhysicalState();
        try {
            delivery.start();
        } catch (IllegalStateException exception) {
            return Result.failure(ApplicationError.conflict("Delivery", exception.getMessage()));
        }
        return commit(delivery, from, "delivery.started.v1",
                (saved, occurredAt) -> new DeliveryStarted(saved.getId(), saved.getOrderId(),
                        saved.getProviderId(), from.name(), occurredAt).toPayloadJson());
    }

    @Override
    @Transactional
    public Result<Delivery, ApplicationError> handle(ArriveDeliveryCommand command) {
        var existing = deliveryRepository.findById(command.deliveryId());
        if (existing.isEmpty()) {
            return notFound(command.deliveryId());
        }
        var delivery = existing.get();
        var from = delivery.currentPhysicalState();
        try {
            delivery.arrive();
        } catch (IllegalStateException exception) {
            return Result.failure(ApplicationError.conflict("Delivery", exception.getMessage()));
        }
        return commit(delivery, from, "delivery.arrived.v1",
                (saved, occurredAt) -> new DeliveryArrived(saved.getId(), saved.getOrderId(),
                        saved.getProviderId(), from.name(), occurredAt).toPayloadJson());
    }

    @Override
    @Transactional
    public Result<Delivery, ApplicationError> handle(CompletePhysicalDeliveryCommand command) {
        var existing = deliveryRepository.findById(command.deliveryId());
        if (existing.isEmpty()) {
            return notFound(command.deliveryId());
        }
        var delivery = existing.get();
        // U11 keeps *both* volumes, so a close with an unresolvable requested volume would persist an
        // ambiguous `requestedVolume=null` beside a real delivered one — the exact state the invariant exists
        // to prevent. Refuse the close instead of accepting an incomplete evidence silently.
        var requestedVolume = fuelOrderQueryService.findRequestedQuantity(delivery.getOrderId());
        if (requestedVolume.isEmpty()) {
            return Result.failure(ApplicationError.businessRuleViolation("delivery.complete",
                    "The requested volume of the order could not be resolved; the physical close needs it as evidence"));
        }
        var occurredAt = Instant.now();
        // A close issued from ARRIVED records the discharge too: S14 defines no separate discharge command.
        var discharge = delivery.currentPhysicalState() == DeliveryPhysicalState.ARRIVED;
        try {
            // Evidence is checked before touching the aggregate, so a rejected close mutates nothing.
            Delivery.validateEvidence(command.deliveredVolume(), requestedVolume.get());
            // The discharge is its own observed transition, so it gets its own save and its own version: the
            // journal row must carry the version the aggregate really held in DELIVERING, not the
            // post-completion one. Both saves share this transaction, and the row lock taken by the first one
            // makes a race on the second impossible — a rejected or racing close still leaves nothing behind.
            if (discharge) {
                delivery.beginDelivering();
                delivery = deliveryRepository.saveAndFlush(delivery);
                transitionRepository.add(new DeliveryStateTransition(null, delivery.getId(),
                        DeliveryPhysicalState.ARRIVED, DeliveryPhysicalState.DELIVERING,
                        delivery.getVersion(), occurredAt));
            }
            delivery.completePhysical(command.deliveredVolume(), requestedVolume.get());
            var saved = deliveryRepository.saveAndFlush(delivery);
            transitionRepository.add(new DeliveryStateTransition(null, saved.getId(),
                    DeliveryPhysicalState.DELIVERING, DeliveryPhysicalState.COMPLETED,
                    saved.getVersion(), occurredAt));
            publicationRegistry.publish("delivery.completed.v1", AGGREGATE_TYPE, String.valueOf(saved.getId()),
                    saved.getProviderId(), (long) saved.getVersion(),
                    new DeliveryCompleted(saved.getId(), saved.getOrderId(), saved.getProviderId(),
                            saved.getDeliveredVolume(), saved.getRequestedVolume(), occurredAt).toPayloadJson());
            return Result.success(saved);
        } catch (IllegalArgumentException exception) {
            return Result.failure(ApplicationError.validationError("deliveredVolume", exception.getMessage()));
        } catch (IllegalStateException exception) {
            return Result.failure(ApplicationError.conflict("Delivery", exception.getMessage()));
        } catch (OptimisticLockingFailureException exception) {
            return concurrent();
        }
    }

    @Override
    @Transactional
    public Result<Delivery, ApplicationError> handle(FailDeliveryCommand command) {
        return terminate(command.deliveryId(), DeliveryPhysicalState.FAILED, command.reason());
    }

    @Override
    @Transactional
    public Result<Delivery, ApplicationError> handle(CancelDeliveryCommand command) {
        return terminate(command.deliveryId(), DeliveryPhysicalState.CANCELLED, command.reason());
    }

    private Result<Delivery, ApplicationError> terminate(Long deliveryId,
                                                        DeliveryPhysicalState terminalState,
                                                        String reason) {
        var existing = deliveryRepository.findById(deliveryId);
        if (existing.isEmpty()) {
            return notFound(deliveryId);
        }
        var delivery = existing.get();
        var from = delivery.currentPhysicalState();
        try {
            if (terminalState == DeliveryPhysicalState.CANCELLED) {
                delivery.cancel(reason);
            } else {
                delivery.failPhysical(reason);
            }
        } catch (IllegalStateException exception) {
            return Result.failure(ApplicationError.conflict("Delivery", exception.getMessage()));
        }
        return commit(delivery, from, "delivery.failed.v1",
                (saved, occurredAt) -> new DeliveryFailed(saved.getId(), saved.getOrderId(),
                        saved.getProviderId(), terminalState.name(), reason, occurredAt).toPayloadJson());
    }

    /** Persists the transition, journals it and publishes the event — one transactional unit. */
    private Result<Delivery, ApplicationError> commit(Delivery delivery,
                                                     DeliveryPhysicalState from,
                                                     String eventType,
                                                     BiFunction<Delivery, Instant, String> payloadBuilder) {
        var occurredAt = Instant.now();
        try {
            var saved = deliveryRepository.saveAndFlush(delivery);
            transitionRepository.add(new DeliveryStateTransition(null, saved.getId(), from,
                    saved.currentPhysicalState(), saved.getVersion(), occurredAt));
            publicationRegistry.publish(eventType, AGGREGATE_TYPE, String.valueOf(saved.getId()),
                    saved.getProviderId(), (long) saved.getVersion(), payloadBuilder.apply(saved, occurredAt));
            return Result.success(saved);
        } catch (OptimisticLockingFailureException exception) {
            return concurrent();
        }
    }

    private static Result<Delivery, ApplicationError> notFound(Long deliveryId) {
        return Result.failure(ApplicationError.notFound("Delivery", String.valueOf(deliveryId)));
    }

    private static Result<Delivery, ApplicationError> concurrent() {
        return Result.failure(ApplicationError.conflict("Delivery", "The delivery was advanced concurrently"));
    }
}
