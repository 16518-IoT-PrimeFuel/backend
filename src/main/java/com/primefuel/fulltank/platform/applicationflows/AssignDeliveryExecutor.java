package com.primefuel.fulltank.platform.applicationflows;

import com.primefuel.fulltank.platform.fleet.api.FleetReservations;
import com.primefuel.fulltank.platform.fleet.domain.model.commands.ReserveFleetCommand;
import com.primefuel.fulltank.platform.fulfillment.api.DeliveryAssignments;
import com.primefuel.fulltank.platform.replenishment.api.ReplenishmentAcceptance;
import com.primefuel.fulltank.platform.replenishment.api.ReplenishmentLookup;
import com.primefuel.fulltank.platform.shared.application.result.ApplicationError;
import com.primefuel.fulltank.platform.shared.application.result.Result;
import com.primefuel.fulltank.platform.supply.api.SupplyReservations;
import com.primefuel.fulltank.platform.supply.domain.model.commands.ReserveSupplyCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The transactional half of the S15/T15-A orchestrator. It runs all four steps in a <strong>single local
 * transaction</strong> and signals any step failure by <em>throwing</em> {@link AssignmentFailedException},
 * so the transaction rolls back atomically: a failure cannot leave a consumed acceptance or an orphan
 * reservation behind. Compensation is the rollback itself — there is no half-applied state to repair.
 *
 * <p>Kept separate from {@link AssignDeliveryFlow} (which is not transactional) so the rollback propagates
 * through a proxy instead of being flattened by a normal return. {@code READ_COMMITTED} matches the fleet
 * reservation's own isolation (T13-B), which re-reads its idempotency key after the resource lock.
 */
@Service
public class AssignDeliveryExecutor {

    private static final Logger LOG = LoggerFactory.getLogger(AssignDeliveryExecutor.class);
    private static final String ACCEPTED = "ACCEPTED";

    private final ReplenishmentLookup replenishmentLookup;
    private final ReplenishmentAcceptance replenishmentAcceptance;
    private final SupplyReservations supplyReservations;
    private final FleetReservations fleetReservations;
    private final DeliveryAssignments deliveryAssignments;

    public AssignDeliveryExecutor(ReplenishmentLookup replenishmentLookup,
                                  ReplenishmentAcceptance replenishmentAcceptance,
                                  SupplyReservations supplyReservations,
                                  FleetReservations fleetReservations,
                                  DeliveryAssignments deliveryAssignments) {
        this.replenishmentLookup = replenishmentLookup;
        this.replenishmentAcceptance = replenishmentAcceptance;
        this.supplyReservations = supplyReservations;
        this.fleetReservations = fleetReservations;
        this.deliveryAssignments = deliveryAssignments;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AssignDeliveryResult execute(AssignDeliveryFlowCommand command) {
        if (command.commandId() == null || command.commandId().isBlank()) {
            throw fail(ApplicationError.validationError("commandId", "A command id is required"));
        }
        if (command.orderId() == null) {
            throw fail(ApplicationError.validationError("orderId", "An order is required"));
        }

        // Idempotency (A3): a retried command returns the delivery it already produced, reserving nothing.
        var existing = deliveryAssignments.findByAssignmentCommandId(command.commandId());
        if (existing.isPresent()) {
            if (!command.orderId().equals(existing.get().orderId())) {
                throw fail(ApplicationError.conflict("Delivery",
                        "The command id is already used by a different order"));
            }
            return result(existing.get(), null, null, command.commandId());
        }

        // The acceptance behind the legacy order is the authority for what to reserve (and that it is owned
        // by the caller's provider). No accepted request behind the order ⇒ 404.
        var found = replenishmentLookup.findByOrderId(command.orderId());
        if (found.isEmpty() || !command.providerId().equals(found.get().providerId())) {
            throw fail(ApplicationError.notFound("ReplenishmentRequest", "order " + command.orderId()));
        }
        var request = found.get();
        if (!ACCEPTED.equals(request.status())) {
            throw fail(ApplicationError.conflict("Delivery",
                    "The order's replenishment request is not accepted"));
        }

        // Step 1 — consume the acceptance once-only: the gate that prevents assigning the same need twice.
        var consumed = replenishmentAcceptance.consume(request.id());
        if (consumed.isFailure()) {
            throw fail(errorOf(consumed));
        }
        if (!consumed.getOrElse(false)) {
            throw fail(ApplicationError.conflict("ReplenishmentRequest",
                    "The request's acceptance was already consumed"));
        }

        // Step 2 — hold the supply (exclusive, revalidated under the product lock inside supply).
        var supply = supplyReservations.reserve(new ReserveSupplyCommand(request.providerId(),
                request.fuelProductId(), command.commandId(), request.quantity(), request.unit()));
        if (supply.isFailure()) {
            throw fail(errorOf(supply));
        }

        // Step 3 — hold the fleet (exclusive, revalidated under the driver/tanker lock inside fleet).
        var fleet = fleetReservations.reserve(new ReserveFleetCommand(request.providerId(), command.driverId(),
                command.tankerId(), command.commandId(), command.windowStart(), command.windowEnd(),
                request.quantity(), request.unit()));
        if (fleet.isFailure()) {
            throw fail(errorOf(fleet));
        }

        // Step 4 — materialise the assigned delivery (assigned, not started).
        var delivery = deliveryAssignments.createAssigned(new DeliveryAssignments.CreateAssignedDeliveryCommand(
                command.commandId(), command.orderId(), request.providerId(), command.driverId(),
                command.tankerId(), command.scheduledDate(), command.notes()));
        if (delivery.isFailure()) {
            throw fail(errorOf(delivery));
        }

        LOG.info("assignment committed commandId={} orderId={} deliveryId={} driverId={} tankerId={}",
                command.commandId(), command.orderId(), delivery.getOrElse(null).id(), command.driverId(),
                command.tankerId());
        return result(delivery.getOrElse(null), supply.getOrElse(null).id(), fleet.getOrElse(null).id(),
                command.commandId());
    }

    private static AssignDeliveryResult result(DeliveryAssignments.DeliveryAssignmentSnapshot delivery,
                                               Long supplyReservationId, Long fleetReservationId,
                                               String commandId) {
        return new AssignDeliveryResult(delivery.id(), delivery.orderId(), delivery.providerId(),
                delivery.driverId(), delivery.vehicleId(), supplyReservationId, fleetReservationId,
                delivery.physicalState(), commandId);
    }

    private static AssignmentFailedException fail(ApplicationError error) {
        return new AssignmentFailedException(error);
    }

    /** Bridges a failed step's error (all seams share {@link ApplicationError}) into the thrown signal. */
    private static <T> ApplicationError errorOf(Result<T, ApplicationError> failure) {
        return switch (failure) {
            case Result.Failure<T, ApplicationError> f -> f.error();
            case Result.Success<T, ApplicationError> ignored ->
                    throw new IllegalArgumentException("Expected a failed result");
        };
    }
}
