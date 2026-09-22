package com.primefuel.fulltank.platform.fulfillment.application.internal.commandservices;

import com.primefuel.fulltank.platform.equipment.domain.repositories.EquipmentRepository;
import com.primefuel.fulltank.platform.fulfillment.application.commandservices.DeliveryCommandService;
import com.primefuel.fulltank.platform.fulfillment.application.commandservices.DeliveryLifecycleService;
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
import com.primefuel.fulltank.platform.fleet.domain.repositories.DriverRepository;
import com.primefuel.fulltank.platform.fleet.domain.repositories.TankerRepository;
import com.primefuel.fulltank.platform.inventory.domain.repositories.FuelProductRepository;
import com.primefuel.fulltank.platform.ordering.application.queryservices.FuelOrderQueryService;
import com.primefuel.fulltank.platform.ordering.domain.repositories.FuelOrderRepository;
import com.primefuel.fulltank.platform.shared.application.result.ApplicationError;
import com.primefuel.fulltank.platform.shared.application.result.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * T14-B legacy delivery adapter. It keeps the v1 (S14) contract intact — same routes, same request bodies,
 * same legacy {@code status} in responses — while routing every delivery state mutation through the
 * physical machine ({@link DeliveryLifecycleService}) instead of mutating the aggregate directly. The
 * v1↔physical map is:
 *
 * <ul>
 *   <li>{@code POST /deliveries} (create) — unchanged: it still creates a dispatched delivery. Its legacy
 *       {@code status=DISPATCHED} is read as physical {@code ASSIGNED} through the compatibility map; no
 *       {@code physical_state} row is written, exactly like any legacy row.</li>
 *   <li>{@code POST /deliveries/{id}/dispatch} → {@code AssignDeliveryCommand} (legacy dispatch materialises
 *       {@code ASSIGNED}; idempotent).</li>
 *   <li>{@code POST /deliveries/{id}/fail} → {@code FailDeliveryCommand} / {@code failPhysical}.</li>
 *   <li>{@code POST /deliveries/{id}/complete} → {@code CompletePhysicalDeliveryCommand}. v1 carries no
 *       volume and no intermediate states, so the adapter (a) reads the order's requested quantity as the
 *       delivered evidence (legacy "delivered in full", assumption A2) and (b) materialises the states v1
 *       never recorded ({@code ASSIGNED→STARTED→ARRIVED}) before closing (assumption A1). Both are open
 *       assumptions recorded in {@code docs/api-ledger/T14-B-legacy-delivery-lifecycle.md}.</li>
 * </ul>
 *
 * <p>The legacy v1 foreign side effects of {@code complete} (releasing the driver/tanker, refuelling the
 * tank, moving the order to {@code PENDING_PAYMENT}) are kept in {@code handle(CompleteDeliveryCommand)} for
 * contract compatibility: retiring the cross-module writes is T15-B ("borrar imports cross-module"), not
 * T14-B. Payment never touches the physical state — this adapter does not read or write {@code payment}.
 *
 * <p>The two dependencies added by T14-B ({@link DeliveryLifecycleService}, {@link FuelOrderQueryService})
 * are field-injected on purpose so the existing constructor stays byte-for-byte identical to the frozen
 * ArchUnit baseline (same technique as T10-B's legacy bridge).
 */
@Service
public class DeliveryCommandServiceImpl implements DeliveryCommandService {

    private final DeliveryRepository deliveryRepository;
    private final DriverRepository driverRepository;
    private final TankerRepository tankerRepository;
    private final FuelOrderRepository orderRepository;
    private final FuelProductRepository productRepository;
    private final EquipmentRepository equipmentRepository;

    @Autowired
    private DeliveryLifecycleService deliveryLifecycleService;

    /** Read seam for the order's requested quantity; the ordering public API, not {@code ordering.domain}. */
    @Autowired
    private FuelOrderQueryService fuelOrderQueryService;

    public DeliveryCommandServiceImpl(DeliveryRepository deliveryRepository,
                                      DriverRepository driverRepository,
                                      TankerRepository tankerRepository,
                                      FuelOrderRepository orderRepository,
                                      FuelProductRepository productRepository,
                                      EquipmentRepository equipmentRepository) {
        this.deliveryRepository = deliveryRepository;
        this.driverRepository = driverRepository;
        this.tankerRepository = tankerRepository;
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.equipmentRepository = equipmentRepository;
    }

    @Override
    @Transactional
    public Result<Delivery, ApplicationError> handle(CreateDeliveryCommand command) {
        var driver = driverRepository.findById(command.driverId());
        var vehicle = tankerRepository.findById(command.vehicleId());
        if (driver.isEmpty() || vehicle.isEmpty()) {
            return Result.failure(ApplicationError.notFound("Fulfillment resource", "driver or vehicle"));
        }
        if (!command.providerId().equals(driver.get().getProviderId())
                || !command.providerId().equals(vehicle.get().getProviderId())) {
            return Result.failure(ApplicationError.conflict("Delivery",
                    "Driver and vehicle must belong to the selected provider"));
        }
        if (!isAvailable(driver.get().getStatus()) || !isAvailable(vehicle.get().getStatus())) {
            return Result.failure(ApplicationError.conflict("Delivery", "Driver or vehicle is not available"));
        }
        var order = orderRepository.findById(command.orderId());
        if (order.isEmpty() || !command.providerId().equals(order.get().getProviderId())) {
            return Result.failure(ApplicationError.notFound("FuelOrder", command.orderId().toString()));
        }
        if (vehicle.get().getCapacity() < order.get().getRequestedQuantity()) {
            return Result.failure(ApplicationError.conflict("Delivery", "Vehicle capacity is insufficient"));
        }
        var product = productRepository.findById(order.get().getFuelProductId());
        if (product.isEmpty() || product.get().getAvailableStock() < order.get().getRequestedQuantity()) {
            return Result.failure(ApplicationError.conflict("Delivery", "Insufficient inventory stock"));
        }
        if (deliveryRepository.findByOrderId(command.orderId()).isPresent()) {
            return Result.failure(ApplicationError.conflict("Delivery",
                    "A delivery already exists for order " + command.orderId()));
        }
        driver.get().setStatus("ASSIGNED");
        vehicle.get().setStatus("IN_ROUTE");
        product.get().updateStock(product.get().getAvailableStock() - order.get().getRequestedQuantity());
        order.get().dispatch();
        driverRepository.save(driver.get());
        tankerRepository.save(vehicle.get());
        productRepository.save(product.get());
        orderRepository.save(order.get());
        var delivery = new Delivery(command);
        delivery.dispatch();
        return Result.success(deliveryRepository.save(delivery));
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
        // requested quantity (A2). Without it the physical close cannot satisfy U11 and is refused — never
        // persisted as an ambiguous requestedVolume=null beside a real delivered one.
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
        // v1 foreign side effects, kept for contract compatibility (T15-B owns their removal). They run only
        // after the physical close succeeded, in the same transaction.
        driverRepository.findById(delivery.getDriverId()).ifPresent(driver -> {
            driver.setStatus("AVAILABLE");
            driverRepository.save(driver);
        });
        tankerRepository.findById(delivery.getVehicleId()).ifPresent(vehicle -> {
            vehicle.setStatus("AVAILABLE");
            tankerRepository.save(vehicle);
        });
        orderRepository.findById(delivery.getOrderId()).ifPresent(order -> {
            if (order.getEquipmentId() != null) {
                equipmentRepository.findById(order.getEquipmentId()).ifPresent(equipment -> {
                    equipment.receiveFuel(order.getRequestedQuantity());
                    equipmentRepository.save(equipment);
                });
            }
            order.receive();
            orderRepository.save(order);
        });
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

    private static boolean isAvailable(String status) {
        return "AVAILABLE".equalsIgnoreCase(status) || "ACTIVE".equalsIgnoreCase(status);
    }
}
