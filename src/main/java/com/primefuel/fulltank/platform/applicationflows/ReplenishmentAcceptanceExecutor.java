package com.primefuel.fulltank.platform.applicationflows;

import com.primefuel.fulltank.platform.equipment.api.CustomerDirectory;
import com.primefuel.fulltank.platform.equipment.api.TankAssets;
import com.primefuel.fulltank.platform.ordering.api.FuelOrderCreation;
import com.primefuel.fulltank.platform.replenishment.api.ReplenishmentLookup;
import com.primefuel.fulltank.platform.replenishment.application.commandservices.ReplenishmentCommandService;
import com.primefuel.fulltank.platform.replenishment.domain.model.aggregates.ReplenishmentRequest;
import com.primefuel.fulltank.platform.replenishment.domain.model.commands.AcceptReplenishmentRequestCommand;
import com.primefuel.fulltank.platform.replenishment.domain.model.commands.AttachReplenishmentOrderCommand;
import com.primefuel.fulltank.platform.replenishment.domain.model.commands.ConsumeReplenishmentAcceptanceCommand;
import com.primefuel.fulltank.platform.shared.application.result.ApplicationError;
import com.primefuel.fulltank.platform.shared.application.result.Result;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReplenishmentAcceptanceExecutor {

    private final ReplenishmentLookup requests;
    private final ReplenishmentCommandService replenishmentCommands;
    private final CustomerDirectory customers;
    private final TankAssets tanks;
    private final FuelOrderCreation orders;

    public ReplenishmentAcceptanceExecutor(ReplenishmentLookup requests,
                                           ReplenishmentCommandService replenishmentCommands,
                                           CustomerDirectory customers, TankAssets tanks,
                                           FuelOrderCreation orders) {
        this.requests = requests;
        this.replenishmentCommands = replenishmentCommands;
        this.customers = customers;
        this.tanks = tanks;
        this.orders = orders;
    }

    @Transactional
    public ReplenishmentRequest execute(Long requestId) {
        var request = requests.findById(requestId).orElseThrow(() -> fail(
                ApplicationError.notFound("ReplenishmentRequest", String.valueOf(requestId))));
        if (request.deliveryAddress() == null || request.deliveryAddress().isBlank()
                || request.deliveryDate() == null) {
            throw fail(ApplicationError.conflict("ReplenishmentRequest",
                    "The request has no delivery address or date"));
        }
        var accepted = replenishmentCommands.handle(new AcceptReplenishmentRequestCommand(requestId));
        if (accepted.isFailure()) throw fail(AssignDeliveryExecutor.errorOf(accepted));
        var consumed = replenishmentCommands.handle(new ConsumeReplenishmentAcceptanceCommand(requestId));
        if (consumed.isFailure()) throw fail(AssignDeliveryExecutor.errorOf(consumed));
        if (!consumed.getOrElse(false)) {
            throw fail(ApplicationError.conflict("ReplenishmentRequest", "The request was already accepted"));
        }

        var companyId = customers.legacyCompanyIdForCustomer(request.customerAccountId())
                .orElseThrow(() -> fail(ApplicationError.conflict("ReplenishmentRequest",
                        "No legacy company mapping exists for customer account " + request.customerAccountId())));
        // The order's equipment is optional: a tank registered through the API has no legacy equipment.
        var equipmentId = tanks.legacyEquipmentIdForTank(request.tankId()).orElse(null);
        var orderId = orders.create(new FuelOrderCreation.Command(companyId, request.providerId(),
                request.fuelProductId(), equipmentId, request.quantity(), request.deliveryAddress(),
                request.deliveryDate()));
        if (orderId.isFailure()) throw fail(AssignDeliveryExecutor.errorOf(orderId));

        var attached = replenishmentCommands.handle(new AttachReplenishmentOrderCommand(requestId,
                orderId.getOrElse(null)));
        if (attached.isFailure()) throw fail(AssignDeliveryExecutor.errorOf(attached));
        return attached.getOrElse(null);
    }

    private static AssignmentFailedException fail(ApplicationError error) {
        return AssignDeliveryExecutor.fail(error);
    }
}
