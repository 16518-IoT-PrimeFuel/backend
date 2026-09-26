package com.primefuel.fulltank.platform.fulfillment.application.internal.commandservices;

import com.primefuel.fulltank.platform.fulfillment.application.commandservices.DeliveryCommandService;
import com.primefuel.fulltank.platform.fulfillment.application.ports.DeliveryEquipmentPort;
import com.primefuel.fulltank.platform.fulfillment.application.ports.DeliveryOrderPort;
import com.primefuel.fulltank.platform.fulfillment.application.ports.DeliveryJournalStore;
import com.primefuel.fulltank.platform.fulfillment.application.ports.FleetReservation;
import com.primefuel.fulltank.platform.fulfillment.application.ports.FleetReservationStore;
import com.primefuel.fulltank.platform.fulfillment.domain.model.aggregates.Delivery;
import com.primefuel.fulltank.platform.fulfillment.domain.model.commands.ArriveDeliveryCommand;
import com.primefuel.fulltank.platform.fulfillment.domain.model.commands.CompleteDeliveryCommand;
import com.primefuel.fulltank.platform.fulfillment.domain.model.commands.CreateDeliveryCommand;
import com.primefuel.fulltank.platform.fulfillment.domain.model.commands.DispatchDeliveryCommand;
import com.primefuel.fulltank.platform.fulfillment.domain.model.commands.FailDeliveryCommand;
import com.primefuel.fulltank.platform.fulfillment.domain.repositories.DeliveryRepository;
import com.primefuel.fulltank.platform.fulfillment.domain.repositories.DriverRepository;
import com.primefuel.fulltank.platform.fulfillment.domain.repositories.VehicleRepository;
import com.primefuel.fulltank.platform.inventory.application.ports.SupplyReservationStore;
import com.primefuel.fulltank.platform.shared.application.result.ApplicationError;
import com.primefuel.fulltank.platform.shared.application.result.Result;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import java.time.Clock;
import java.time.Instant;

@Service
public class DeliveryCommandServiceImpl implements DeliveryCommandService {
    private final DeliveryRepository deliveries;
    private final DriverRepository drivers;
    private final VehicleRepository vehicles;
    private final SupplyReservationStore supplyReservations;
    private final FleetReservationStore fleetReservations;
    private final DeliveryOrderPort orders;
    private final DeliveryEquipmentPort equipment;
    private final DeliveryJournalStore journal;

    public DeliveryCommandServiceImpl(DeliveryRepository deliveries,
                                      DriverRepository drivers,
                                      VehicleRepository vehicles,
                                      SupplyReservationStore supplyReservations,
                                      FleetReservationStore fleetReservations,
                                      DeliveryOrderPort orders,
                                      DeliveryEquipmentPort equipment,
                                      DeliveryJournalStore journal) {
        this.deliveries = deliveries;
        this.drivers = drivers;
        this.vehicles = vehicles;
        this.supplyReservations = supplyReservations;
        this.fleetReservations = fleetReservations;
        this.orders = orders;
        this.equipment = equipment;
        this.journal = journal;
    }

    @Override
    @Transactional
    public Result<Delivery, ApplicationError> handle(CreateDeliveryCommand command) {
        var driver = drivers.findById(command.driverId());
        var vehicle = vehicles.findById(command.vehicleId());
        if (driver.isEmpty() || vehicle.isEmpty()) {
            return Result.failure(ApplicationError.notFound("Fulfillment resource", "driver or vehicle"));
        }
        if (!command.providerId().equals(driver.get().getProviderId())
                || !command.providerId().equals(vehicle.get().getProviderId())) {
            return Result.failure(ApplicationError.conflict("Delivery",
                    "Driver and vehicle must belong to the selected provider"));
        }
        if (!driver.get().isEligibleAt(Clock.systemUTC()) || !vehicle.get().isEligible()) {
            return Result.failure(ApplicationError.conflict("Delivery", "Driver or vehicle is not available"));
        }
        var order = orders.findById(command.orderId());
        if (order.isEmpty() || !command.providerId().equals(order.get().providerId())) {
            return Result.failure(ApplicationError.notFound("FuelOrder", command.orderId().toString()));
        }
        if (vehicle.get().getCapacity() < order.get().requestedQuantity()) {
            return Result.failure(ApplicationError.conflict("Delivery", "Vehicle capacity is insufficient"));
        }
        if (deliveries.findByOrderId(command.orderId()).isPresent()) {
            return Result.failure(ApplicationError.conflict("Delivery",
                    "A delivery already exists for order " + command.orderId()));
        }

        if (!"PENDING".equals(order.get().status())) {
            return Result.failure(ApplicationError.conflict("Delivery", "Order is not pending assignment"));
        }

        var fleetKey = "delivery:" + command.orderId();
        var windowStart = scheduledStart(command.scheduledDate());
        var reservationKey = order.get().requestId() != null ? order.get().requestId() : order.get().id();
        if (order.get().requestId() == null && !supplyReservations.reserve(reservationKey,
                order.get().fuelProductId(), order.get().requestedQuantity())) {
            return Result.failure(ApplicationError.conflict("Delivery", "Insufficient inventory stock"));
        }
        if (!fleetReservations.reserve(new FleetReservation(fleetKey, command.providerId(), null,
                command.driverId(), command.vehicleId(), windowStart, windowStart.plusSeconds(86_400),
                order.get().requestedQuantity()))) {
            return Result.failure(ApplicationError.conflict("Delivery", "Driver or vehicle is already reserved"));
        }
        if (!orders.dispatch(command.orderId())) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return Result.failure(ApplicationError.conflict("Delivery", "Order cannot be dispatched"));
        }
        driver.get().setStatus("ASSIGNED");
        vehicle.get().setStatus("IN_ROUTE");
        drivers.save(driver.get());
        vehicles.save(vehicle.get());
        var delivery = deliveries.save(new Delivery(command));
        journal.append("delivery-assigned:" + command.orderId(), delivery.getId(), "DELIVERY_ASSIGNED",
                Instant.now(), "orderId=" + command.orderId());
        return Result.success(delivery);
    }

    @Override
    public Result<Delivery, ApplicationError> handle(DispatchDeliveryCommand command) {
        var existing = deliveries.findById(command.deliveryId());
        if (existing.isEmpty()) return Result.failure(ApplicationError.notFound("Delivery", command.deliveryId().toString()));
        try {
            var delivery = existing.get();
            delivery.dispatch();
            var saved = deliveries.save(delivery);
            journal.append("delivery-dispatched:" + delivery.getId(), delivery.getId(), "DELIVERY_DISPATCHED",
                    Instant.now(), "deliveryId=" + delivery.getId());
            return Result.success(saved);
        } catch (IllegalStateException exception) {
            return Result.failure(ApplicationError.conflict("Delivery", exception.getMessage()));
        }
    }

    @Override
    @Transactional
    public Result<Delivery, ApplicationError> handle(CompleteDeliveryCommand command) {
        var existing = deliveries.findById(command.deliveryId());
        if (existing.isEmpty()) return Result.failure(ApplicationError.notFound("Delivery", command.deliveryId().toString()));
        var delivery = existing.get();
        var order = orders.findById(delivery.getOrderId());
        if (order.isEmpty()) return Result.failure(ApplicationError.notFound("FuelOrder", delivery.getOrderId().toString()));
        if (!"DISPATCHED".equals(order.get().status())) {
            return Result.failure(ApplicationError.conflict("Delivery", "Order is not dispatched"));
        }
        try {
            delivery.complete();
        } catch (IllegalStateException exception) {
            return Result.failure(ApplicationError.conflict("Delivery", exception.getMessage()));
        }
        if (order.get().equipmentId() != null
                && !equipment.receiveFuel(order.get().equipmentId(), order.get().requestedQuantity())) {
            return Result.failure(ApplicationError.notFound("Equipment", order.get().equipmentId().toString()));
        }
        if (!orders.receive(delivery.getOrderId())) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return Result.failure(ApplicationError.conflict("Delivery", "Order cannot be received"));
        }
        drivers.findById(delivery.getDriverId()).ifPresent(driver -> {
            driver.setStatus("AVAILABLE");
            drivers.save(driver);
        });
        vehicles.findById(delivery.getVehicleId()).ifPresent(vehicle -> {
            vehicle.setStatus("AVAILABLE");
            vehicles.save(vehicle);
        });
        fleetReservations.release(delivery.getProviderId(), "delivery:" + delivery.getOrderId());
        var saved = deliveries.save(delivery);
        journal.append("delivery-completed:" + delivery.getId(), delivery.getId(), "DELIVERY_COMPLETED",
                Instant.now(), "deliveryId=" + delivery.getId());
        return Result.success(saved);
    }

    @Override
    public Result<Delivery, ApplicationError> handle(ArriveDeliveryCommand command) {
        var existing = deliveries.findById(command.deliveryId());
        if (existing.isEmpty()) return Result.failure(ApplicationError.notFound("Delivery", command.deliveryId().toString()));
        try {
            var delivery = existing.get();
            delivery.arrive();
            var saved = deliveries.save(delivery);
            journal.append("delivery-arrived:" + delivery.getId(), delivery.getId(), "DELIVERY_ARRIVED",
                    Instant.now(), "deliveryId=" + delivery.getId());
            return Result.success(saved);
        } catch (IllegalStateException exception) {
            return Result.failure(ApplicationError.conflict("Delivery", exception.getMessage()));
        }
    }

    @Override
    public Result<Delivery, ApplicationError> handle(FailDeliveryCommand command) {
        var existing = deliveries.findById(command.deliveryId());
        if (existing.isEmpty()) return Result.failure(ApplicationError.notFound("Delivery", command.deliveryId().toString()));
        var delivery = existing.get();
        delivery.fail(command.reason());
        fleetReservations.release(delivery.getProviderId(), "delivery:" + delivery.getOrderId());
        var saved = deliveries.save(delivery);
        journal.append("delivery-failed:" + delivery.getId(), delivery.getId(), "DELIVERY_FAILED",
                Instant.now(), command.reason());
        return Result.success(saved);
    }

    private static Instant scheduledStart(String scheduledDate) {
        if (scheduledDate == null || scheduledDate.isBlank()) return Instant.now();
        return Instant.parse(scheduledDate + "T00:00:00Z");
    }
}
