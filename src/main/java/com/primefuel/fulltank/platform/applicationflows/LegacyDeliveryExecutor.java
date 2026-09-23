package com.primefuel.fulltank.platform.applicationflows;

import com.primefuel.fulltank.platform.equipment.domain.repositories.EquipmentRepository;
import com.primefuel.fulltank.platform.fleet.api.FleetReservations;
import com.primefuel.fulltank.platform.fleet.domain.repositories.DriverRepository;
import com.primefuel.fulltank.platform.fleet.domain.repositories.TankerRepository;
import com.primefuel.fulltank.platform.fulfillment.api.DeliveryAssignments;
import com.primefuel.fulltank.platform.fulfillment.api.DeliveryIntegration;
import com.primefuel.fulltank.platform.inventory.domain.repositories.FuelProductRepository;
import com.primefuel.fulltank.platform.ordering.domain.repositories.FuelOrderRepository;
import com.primefuel.fulltank.platform.replenishment.api.ReplenishmentLookup;
import com.primefuel.fulltank.platform.shared.application.result.ApplicationError;
import com.primefuel.fulltank.platform.shared.application.result.Result;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;

/**
 * The v1 ({@code POST /api/v1/deliveries}) create orchestration, moved out of {@code fulfillment} so the
 * delivery module owns no foreign repository. It is <strong>dual-mode</strong>:
 *
 * <ul>
 *   <li>An order backed by an {@code ACCEPTED} replenishment request is assigned through
 *       {@link AssignDeliveryExecutor} — the same exclusive-reservation orchestrator v2 uses — so v1 inherits
 *       the same race safety (one winner under the fleet lock) and idempotency ({@code commandId}) as v2.</li>
 *   <li>A direct legacy order (no accepted request) keeps its original side effects: driver/vehicle reserved
 *       in the legacy sense (status), stock decremented and the order dispatched.</li>
 * </ul>
 *
 * <p>Both branches dispatch the legacy order, so the v1 contract and the golden path to
 * {@code PENDING_PAYMENT} on completion are preserved. Any failure throws {@link AssignmentFailedException}
 * so the single transaction rolls back everything (no consumed acceptance, no orphan reservation, no
 * half-applied legacy effects).
 */
@Service
public class LegacyDeliveryExecutor {

    private static final String ACCEPTED = "ACCEPTED";
    private static final Duration DEFAULT_WINDOW = Duration.ofHours(8);

    private final ReplenishmentLookup replenishmentLookup;
    private final AssignDeliveryExecutor assignDeliveryExecutor;
    private final DeliveryAssignments deliveryAssignments;
    private final FleetReservations fleetReservations;
    private final DriverRepository driverRepository;
    private final TankerRepository tankerRepository;
    private final FuelProductRepository productRepository;
    private final FuelOrderRepository orderRepository;
    private final EquipmentRepository equipmentRepository;
    private final Clock clock;

    public LegacyDeliveryExecutor(ReplenishmentLookup replenishmentLookup,
                                  AssignDeliveryExecutor assignDeliveryExecutor,
                                  DeliveryAssignments deliveryAssignments,
                                  FleetReservations fleetReservations,
                                  DriverRepository driverRepository,
                                  TankerRepository tankerRepository,
                                  FuelProductRepository productRepository,
                                  FuelOrderRepository orderRepository,
                                  EquipmentRepository equipmentRepository,
                                  Clock clock) {
        this.replenishmentLookup = replenishmentLookup;
        this.assignDeliveryExecutor = assignDeliveryExecutor;
        this.deliveryAssignments = deliveryAssignments;
        this.fleetReservations = fleetReservations;
        this.driverRepository = driverRepository;
        this.tankerRepository = tankerRepository;
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.equipmentRepository = equipmentRepository;
        this.clock = clock;
    }

    /** Idempotent per order: a retry returns the delivery the command already produced. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Long createDelivery(DeliveryIntegration.CreateLegacyDeliveryCommand command) {
        String commandId = "delivery-create:" + command.orderId();
        var existing = deliveryAssignments.findByAssignmentCommandId(commandId);
        if (existing.isPresent()) {
            return existing.get().id();
        }

        var request = replenishmentLookup.findByOrderId(command.orderId());
        if (request.isPresent() && ACCEPTED.equals(request.get().status())
                && command.providerId().equals(request.get().providerId())) {
            return assignThroughReservations(command, commandId);
        }
        return legacyCreate(command, commandId);
    }

    /** Accepted order: the reservation orchestrator assigns (races serialise under the fleet lock). */
    private Long assignThroughReservations(DeliveryIntegration.CreateLegacyDeliveryCommand command, String commandId) {
        var start = clock.instant();
        // Acceptance already consumed by the T10-B legacy accept bridge (it created the order), so do not
        // consume it here again.
        var result = assignDeliveryExecutor.execute(new AssignDeliveryFlowCommand(commandId, command.orderId(),
                command.providerId(), command.driverId(), command.vehicleId(), start, start.plus(DEFAULT_WINDOW),
                command.scheduledDate(), command.notes()), false);
        // v1 semantics: assigning the delivery dispatches the order (the golden path relies on it).
        dispatchOrder(command.orderId());
        return result.deliveryId();
    }

    /** Direct legacy order: reproduce the original create side effects, then create the legacy delivery. */
    private Long legacyCreate(DeliveryIntegration.CreateLegacyDeliveryCommand command, String commandId) {
        var driver = driverRepository.findById(command.driverId());
        var vehicle = tankerRepository.findById(command.vehicleId());
        if (driver.isEmpty() || vehicle.isEmpty()) {
            throw fail(ApplicationError.notFound("Fulfillment resource", "driver or vehicle"));
        }
        if (!command.providerId().equals(driver.get().getProviderId())
                || !command.providerId().equals(vehicle.get().getProviderId())) {
            throw fail(ApplicationError.conflict("Delivery", "Driver and vehicle must belong to the selected provider"));
        }
        if (!isAvailable(driver.get().getStatus()) || !isAvailable(vehicle.get().getStatus())) {
            throw fail(ApplicationError.conflict("Delivery", "Driver or vehicle is not available"));
        }
        var order = orderRepository.findById(command.orderId());
        if (order.isEmpty() || !command.providerId().equals(order.get().getProviderId())) {
            throw fail(ApplicationError.notFound("FuelOrder", command.orderId().toString()));
        }
        if (vehicle.get().getCapacity() < order.get().getRequestedQuantity()) {
            throw fail(ApplicationError.conflict("Delivery", "Vehicle capacity is insufficient"));
        }
        var product = productRepository.findById(order.get().getFuelProductId());
        if (product.isEmpty() || product.get().getAvailableStock() < order.get().getRequestedQuantity()) {
            throw fail(ApplicationError.conflict("Delivery", "Insufficient inventory stock"));
        }
        driver.get().setStatus("ASSIGNED");
        vehicle.get().setStatus("IN_ROUTE");
        product.get().updateStock(product.get().getAvailableStock() - order.get().getRequestedQuantity());
        order.get().dispatch();
        driverRepository.save(driver.get());
        tankerRepository.save(vehicle.get());
        productRepository.save(product.get());
        orderRepository.save(order.get());
        var created = deliveryAssignments.createLegacy(new DeliveryAssignments.CreateLegacyDeliveryCommand(
                commandId, command.orderId(), command.providerId(), command.driverId(), command.vehicleId(),
                command.scheduledDate(), command.notes()));
        if (created.isFailure()) {
            throw fail(errorOf(created));
        }
        return created.getOrElse(null).id();
    }

    /** Legacy v1 completion side effects, executed inside the caller's (complete) transaction. */
    @Transactional
    public void applyCompletionEffects(DeliveryIntegration.CompletionEffectsCommand command) {
        driverRepository.findById(command.driverId()).ifPresent(driver -> {
            driver.setStatus("AVAILABLE");
            driverRepository.save(driver);
        });
        tankerRepository.findById(command.vehicleId()).ifPresent(vehicle -> {
            vehicle.setStatus("AVAILABLE");
            tankerRepository.save(vehicle);
        });
        if (command.assignmentReference() != null) {
            // A delivery assigned through the reservation flow holds a fleet reservation; end it.
            fleetReservations.release(command.assignmentReference());
        }
        orderRepository.findById(command.orderId()).ifPresent(order -> {
            if (order.getEquipmentId() != null) {
                equipmentRepository.findById(order.getEquipmentId()).ifPresent(equipment -> {
                    equipment.receiveFuel(order.getRequestedQuantity());
                    equipmentRepository.save(equipment);
                });
            }
            order.receive();
            orderRepository.save(order);
        });
    }

    private void dispatchOrder(Long orderId) {
        orderRepository.findById(orderId).ifPresent(order -> {
            order.dispatch();
            orderRepository.save(order);
        });
    }

    private static boolean isAvailable(String status) {
        return "AVAILABLE".equalsIgnoreCase(status) || "ACTIVE".equalsIgnoreCase(status);
    }

    private static AssignmentFailedException fail(ApplicationError error) {
        return new AssignmentFailedException(error);
    }

    private static <T> ApplicationError errorOf(Result<T, ApplicationError> failure) {
        return switch (failure) {
            case Result.Failure<T, ApplicationError> f -> f.error();
            case Result.Success<T, ApplicationError> ignored ->
                    throw new IllegalArgumentException("Expected a failed result");
        };
    }
}
