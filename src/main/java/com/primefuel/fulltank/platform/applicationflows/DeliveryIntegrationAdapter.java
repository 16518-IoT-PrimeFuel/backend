package com.primefuel.fulltank.platform.applicationflows;

import com.primefuel.fulltank.platform.equipment.domain.repositories.EquipmentRepository;
import com.primefuel.fulltank.platform.fleet.api.FleetReservations;
import com.primefuel.fulltank.platform.fulfillment.api.DeliveryIntegration;
import com.primefuel.fulltank.platform.iam.api.LegacyCompanyDirectory;
import com.primefuel.fulltank.platform.inventory.domain.repositories.FuelProductRepository;
import com.primefuel.fulltank.platform.ordering.domain.model.valueobjects.OrderStatus;
import com.primefuel.fulltank.platform.ordering.domain.repositories.FuelOrderRepository;
import com.primefuel.fulltank.platform.replenishment.api.ReplenishmentLookup;
import com.primefuel.fulltank.platform.supply.api.SupplyReservations;
import org.springframework.stereotype.Component;

/**
 * Adapter for the fulfillment-owned {@link DeliveryIntegration} port, implemented in the composition root so
 * {@code fulfillment} applies the delivery side effects without importing any foreign repository. Runs in
 * the caller's transaction, so a failure rolls the whole close back.
 */
@Component("deliveryIntegration")
public class DeliveryIntegrationAdapter implements DeliveryIntegration {

    private final FleetReservations fleetReservations;
    private final SupplyReservations supplyReservations;
    private final FuelProductRepository productRepository;
    private final FuelOrderRepository orderRepository;
    private final EquipmentRepository equipmentRepository;
    private final ReplenishmentLookup replenishmentLookup;
    private final LegacyCompanyDirectory companyDirectory;

    public DeliveryIntegrationAdapter(FleetReservations fleetReservations,
                                     SupplyReservations supplyReservations,
                                     FuelProductRepository productRepository,
                                     FuelOrderRepository orderRepository,
                                     EquipmentRepository equipmentRepository,
                                     ReplenishmentLookup replenishmentLookup,
                                     LegacyCompanyDirectory companyDirectory) {
        this.fleetReservations = fleetReservations;
        this.supplyReservations = supplyReservations;
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.equipmentRepository = equipmentRepository;
        this.replenishmentLookup = replenishmentLookup;
        this.companyDirectory = companyDirectory;
    }

    @Override
    public void applyCompletionEffects(CompletionEffectsCommand command) {
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

    @Override
    public void reopenOrder(Long orderId, Long driverId, Long vehicleId) {
        orderRepository.findById(orderId).filter(order -> order.getStatus() == OrderStatus.DISPATCHED)
                .ifPresent(order -> {
                    order.reopen();
                    orderRepository.save(order);
                });
    }

    @Override
    public java.util.Optional<Long> buyerOrganizationId(Long orderId) {
        return replenishmentLookup.findByOrderId(orderId)
                .map(ReplenishmentLookup.ReplenishmentView::organizationId);
    }

    @Override
    public java.util.Optional<Long> providerOrganizationId(Long providerId) {
        return companyDirectory.organizationIdForProvider(providerId);
    }
}
