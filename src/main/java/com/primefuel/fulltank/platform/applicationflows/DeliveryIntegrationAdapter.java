package com.primefuel.fulltank.platform.applicationflows;

import com.primefuel.fulltank.platform.equipment.domain.repositories.EquipmentRepository;
import com.primefuel.fulltank.platform.fleet.api.FleetReservations;
import com.primefuel.fulltank.platform.fleet.domain.repositories.DriverRepository;
import com.primefuel.fulltank.platform.fleet.domain.repositories.TankerRepository;
import com.primefuel.fulltank.platform.fulfillment.api.DeliveryIntegration;
import com.primefuel.fulltank.platform.inventory.domain.repositories.FuelProductRepository;
import com.primefuel.fulltank.platform.ordering.domain.model.valueobjects.OrderStatus;
import com.primefuel.fulltank.platform.ordering.domain.repositories.FuelOrderRepository;
import com.primefuel.fulltank.platform.supply.api.SupplyReservations;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Adapter for the fulfillment-owned {@link DeliveryIntegration} port, implemented in the composition root so
 * {@code fulfillment} applies the delivery side effects without importing any foreign repository. Runs in
 * the caller's transaction, so a failure rolls the whole close back.
 */
@Component("deliveryIntegration")
public class DeliveryIntegrationAdapter implements DeliveryIntegration {

    /** The statuses the retired direct-create flow wrote; anything else (e.g. SUSPENDED) was set by an admin. */
    private static final Set<String> BUSY = Set.of("ASSIGNED", "IN_ROUTE");

    private final FleetReservations fleetReservations;
    private final SupplyReservations supplyReservations;
    private final DriverRepository driverRepository;
    private final TankerRepository tankerRepository;
    private final FuelProductRepository productRepository;
    private final FuelOrderRepository orderRepository;
    private final EquipmentRepository equipmentRepository;

    public DeliveryIntegrationAdapter(FleetReservations fleetReservations,
                                     SupplyReservations supplyReservations,
                                     DriverRepository driverRepository,
                                     TankerRepository tankerRepository,
                                     FuelProductRepository productRepository,
                                     FuelOrderRepository orderRepository,
                                     EquipmentRepository equipmentRepository) {
        this.fleetReservations = fleetReservations;
        this.supplyReservations = supplyReservations;
        this.driverRepository = driverRepository;
        this.tankerRepository = tankerRepository;
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.equipmentRepository = equipmentRepository;
    }

    @Override
    public void applyCompletionEffects(CompletionEffectsCommand command) {
        freeLegacyFleetStatus(command.driverId(), command.vehicleId());
        var reference = command.assignmentReference();
        if (reference != null) {
            fleetReservations.release(reference);
        }
        // Only stock held by a reservation is decremented here: a delivery from the retired direct-create flow took it at creation.
        long consumed = reference == null ? 0 : supplyReservations.reconcile(reference).getOrElse(0L);
        orderRepository.findById(command.orderId()).ifPresent(order -> {
            if (consumed > 0) {
                productRepository.findById(order.getFuelProductId()).ifPresent(product -> {
                    product.updateStock(Math.max(0.0, product.getAvailableStock() - command.deliveredVolume()));
                    productRepository.save(product);
                });
            }
            if (order.getEquipmentId() != null) {
                equipmentRepository.findById(order.getEquipmentId()).ifPresent(equipment -> {
                    equipment.receiveFuel(command.deliveredVolume());
                    equipmentRepository.save(equipment);
                });
            }
            // Deliveries assigned before assignment dispatched the order still hold it in PENDING/CONFIRMED.
            if (order.getStatus() == OrderStatus.PENDING || order.getStatus() == OrderStatus.CONFIRMED) {
                order.dispatch();
            }
            if (order.getStatus() == OrderStatus.DISPATCHED) {
                order.receive();
            }
            orderRepository.save(order);
        });
    }

    @Override
    public void releaseReservations(String assignmentReference) {
        if (assignmentReference == null) {
            return;
        }
        fleetReservations.release(assignmentReference);
        supplyReservations.release(assignmentReference);
    }

    private void freeLegacyFleetStatus(Long driverId, Long vehicleId) {
        driverRepository.findById(driverId).filter(driver -> BUSY.contains(driver.getStatus())).ifPresent(driver -> {
            driver.setStatus("AVAILABLE");
            driverRepository.save(driver);
        });
        tankerRepository.findById(vehicleId).filter(tanker -> BUSY.contains(tanker.getStatus())).ifPresent(tanker -> {
            tanker.setStatus("AVAILABLE");
            tankerRepository.save(tanker);
        });
    }
}
