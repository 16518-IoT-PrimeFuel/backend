package com.primefuel.fulltank.platform.fulfillment.infrastructure.services;

import com.primefuel.fulltank.platform.fulfillment.api.DeliveryAssignments;
import com.primefuel.fulltank.platform.fulfillment.application.commandservices.DeliveryLifecycleService;
import com.primefuel.fulltank.platform.fulfillment.domain.model.aggregates.Delivery;
import com.primefuel.fulltank.platform.fulfillment.domain.model.commands.AssignDeliveryCommand;
import com.primefuel.fulltank.platform.fulfillment.domain.model.commands.CreateDeliveryCommand;
import com.primefuel.fulltank.platform.fulfillment.domain.repositories.DeliveryRepository;
import com.primefuel.fulltank.platform.shared.application.result.ApplicationError;
import com.primefuel.fulltank.platform.shared.application.result.Result;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Adapter implementing the delivery write seam (S15/T15-A). It creates the delivery and then routes it
 * through the physical machine's {@code assign} so the {@code ASSIGNED} state, its journal row and its
 * {@code DeliveryAssigned} event are produced by T14-A exactly once — the orchestrator never touches the
 * aggregate or starts the delivery.
 */
@Component("deliveryAssignments")
public class DeliveryAssignmentsImpl implements DeliveryAssignments {

    private final DeliveryRepository deliveryRepository;
    private final DeliveryLifecycleService deliveryLifecycleService;

    public DeliveryAssignmentsImpl(DeliveryRepository deliveryRepository,
                                   DeliveryLifecycleService deliveryLifecycleService) {
        this.deliveryRepository = deliveryRepository;
        this.deliveryLifecycleService = deliveryLifecycleService;
    }

    @Override
    public Optional<DeliveryAssignmentSnapshot> findByAssignmentCommandId(String assignmentCommandId) {
        return deliveryRepository.findByAssignmentCommandId(assignmentCommandId)
                .map(DeliveryAssignmentsImpl::toSnapshot);
    }

    @Override
    public Result<DeliveryAssignmentSnapshot, ApplicationError> createLegacy(CreateLegacyDeliveryCommand command) {
        var delivery = new Delivery(new CreateDeliveryCommand(command.orderId(), command.providerId(),
                command.driverId(), command.vehicleId(), command.scheduledDate(), command.notes()));
        delivery.setAssignmentCommandId(command.assignmentCommandId());
        delivery.dispatch();
        return Result.success(toSnapshot(deliveryRepository.saveAndFlush(delivery)));
    }

    @Override
    public Result<DeliveryAssignmentSnapshot, ApplicationError> createAssigned(CreateAssignedDeliveryCommand command) {
        var delivery = new Delivery(new CreateDeliveryCommand(command.orderId(), command.providerId(),
                command.driverId(), command.vehicleId(), command.scheduledDate(), command.notes()));
        delivery.setAssignmentCommandId(command.assignmentCommandId());
        var saved = deliveryRepository.saveAndFlush(delivery);
        // Route through the machine so ASSIGNED is materialised once, with its journal row and event.
        return deliveryLifecycleService.handle(new AssignDeliveryCommand(saved.getId()))
                .map(DeliveryAssignmentsImpl::toSnapshot);
    }

    private static DeliveryAssignmentSnapshot toSnapshot(Delivery delivery) {
        return new DeliveryAssignmentSnapshot(delivery.getId(), delivery.getOrderId(), delivery.getProviderId(),
                delivery.getDriverId(), delivery.getVehicleId(), delivery.currentPhysicalState().name(),
                delivery.getStatus() == null ? null : delivery.getStatus().name(),
                delivery.getRequestedVolume(), delivery.getVersion());
    }
}
