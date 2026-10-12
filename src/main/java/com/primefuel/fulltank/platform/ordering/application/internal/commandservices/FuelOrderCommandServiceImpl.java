package com.primefuel.fulltank.platform.ordering.application.internal.commandservices;

import com.primefuel.fulltank.platform.equipment.application.queryservices.EquipmentQueryService;
import com.primefuel.fulltank.platform.equipment.domain.model.queries.GetEquipmentByIdQuery;
import com.primefuel.fulltank.platform.inventory.application.queryservices.FuelProductQueryService;
import com.primefuel.fulltank.platform.inventory.domain.model.queries.GetFuelProductByIdQuery;
import com.primefuel.fulltank.platform.ordering.application.commandservices.FuelOrderCommandService;
import com.primefuel.fulltank.platform.ordering.domain.model.aggregates.FuelOrder;
import com.primefuel.fulltank.platform.ordering.domain.model.commands.CancelFuelOrderCommand;
import com.primefuel.fulltank.platform.ordering.domain.model.commands.ConfirmFuelOrderCommand;
import com.primefuel.fulltank.platform.ordering.domain.model.commands.CreateFuelOrderCommand;
import com.primefuel.fulltank.platform.ordering.domain.model.commands.ReceiveFuelOrderCommand;
import com.primefuel.fulltank.platform.ordering.domain.model.valueobjects.OrderStatus;
import com.primefuel.fulltank.platform.ordering.domain.repositories.FuelOrderRepository;
import com.primefuel.fulltank.platform.shared.application.result.ApplicationError;
import com.primefuel.fulltank.platform.shared.application.result.Result;
import com.primefuel.fulltank.platform.replenishment.api.ReplenishmentLookup;
import com.primefuel.fulltank.platform.shared.events.EventPublicationRegistry;
import com.primefuel.fulltank.platform.supply.api.SupplyCatalog;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FuelOrderCommandServiceImpl implements FuelOrderCommandService {

    private final FuelOrderRepository fuelOrderRepository;
    private final FuelProductQueryService fuelProductQueryService;
    private final EquipmentQueryService equipmentQueryService;
    private final SupplyCatalog supplyCatalog;
    private final ReplenishmentLookup replenishmentLookup;
    private final EventPublicationRegistry publicationRegistry;

    public FuelOrderCommandServiceImpl(FuelOrderRepository fuelOrderRepository,
                                       FuelProductQueryService fuelProductQueryService,
                                       EquipmentQueryService equipmentQueryService,
                                       SupplyCatalog supplyCatalog,
                                       ReplenishmentLookup replenishmentLookup,
                                       EventPublicationRegistry publicationRegistry) {
        this.fuelOrderRepository = fuelOrderRepository;
        this.fuelProductQueryService = fuelProductQueryService;
        this.equipmentQueryService = equipmentQueryService;
        this.supplyCatalog = supplyCatalog;
        this.replenishmentLookup = replenishmentLookup;
        this.publicationRegistry = publicationRegistry;
    }

    @Override
    @Transactional
    public Result<FuelOrder, ApplicationError> handle(CreateFuelOrderCommand command) {
        if (command.requestedQuantity() == null || !Double.isFinite(command.requestedQuantity())
                || command.requestedQuantity() <= 0) {
            return Result.failure(ApplicationError.validationError(
                    "requestedQuantity", "Requested quantity must be greater than zero"));
        }
        if (command.deliveryAddress() == null || command.deliveryAddress().isBlank()) {
            return Result.failure(ApplicationError.validationError(
                    "deliveryAddress", "A delivery address is required"));
        }
        var productResult = fuelProductQueryService.handle(new GetFuelProductByIdQuery(command.fuelProductId()));
        if (productResult.isEmpty()) {
            return Result.failure(ApplicationError.notFound("FuelProduct", command.fuelProductId().toString()));
        }
        var product = productResult.get();
        if (!product.getProviderId().equals(command.providerId())) {
            return Result.failure(ApplicationError.forbidden(
                    "FuelProduct %s does not belong to provider %s".formatted(
                            command.fuelProductId(), command.providerId())));
        }

        var supply = supplyCatalog.findForTenant(command.providerId(), command.fuelProductId());
        if (supply.isPresent() && !supply.get().active()) {
            return Result.failure(ApplicationError.validationError("fuelProduct", "Fuel product is inactive"));
        }

        if (command.equipmentId() != null) {
            var equipmentResult = equipmentQueryService.handle(new GetEquipmentByIdQuery(command.equipmentId()));
            if (equipmentResult.isEmpty()) {
                return Result.failure(ApplicationError.notFound("Equipment", command.equipmentId().toString()));
            }
            if (!equipmentResult.get().getCompanyId().equals(command.companyId())) {
                return Result.failure(ApplicationError.forbidden(
                        "Equipment %s does not belong to company %s".formatted(
                                command.equipmentId(), command.companyId())));
            }
        }

        var totalPrice = product.getPricePerUnit() * command.requestedQuantity();
        var order = new FuelOrder(command, totalPrice);
        var saved = fuelOrderRepository.save(order);
        return Result.success(saved);
    }

    @Override
    @Transactional
    public Result<FuelOrder, ApplicationError> handle(ConfirmFuelOrderCommand command) {
        var existing = fuelOrderRepository.findById(command.orderId());
        if (existing.isEmpty()) {
            return Result.failure(ApplicationError.notFound("FuelOrder", command.orderId().toString()));
        }
        var order = existing.get();
        try {
            order.confirm();
        } catch (IllegalStateException exception) {
            return Result.failure(ApplicationError.conflict("FuelOrder", exception.getMessage()));
        }
        return Result.success(fuelOrderRepository.save(order));
    }

    @Override
    @Transactional
    public Result<FuelOrder, ApplicationError> handle(CancelFuelOrderCommand command) {
        var existing = fuelOrderRepository.findById(command.orderId());
        if (existing.isEmpty()) {
            return Result.failure(ApplicationError.notFound("FuelOrder", command.orderId().toString()));
        }
        var order = existing.get();
        var alreadyCancelled = order.getStatus() == OrderStatus.CANCELLED;
        try {
            order.cancel();
        } catch (IllegalStateException exception) {
            return Result.failure(ApplicationError.conflict("FuelOrder", exception.getMessage()));
        }
        var saved = fuelOrderRepository.save(order);
        if (!alreadyCancelled) {
            publishCancelled(saved);
        }
        return Result.success(saved);
    }

    @Override
    @Transactional
    public Result<FuelOrder, ApplicationError> handle(ReceiveFuelOrderCommand command) {
        var existing = fuelOrderRepository.findById(command.orderId());
        if (existing.isEmpty()) {
            return Result.failure(ApplicationError.notFound("FuelOrder", command.orderId().toString()));
        }
        var order = existing.get();
        try {
            order.receive();
        } catch (IllegalStateException exception) {
            return Result.failure(ApplicationError.conflict("FuelOrder", exception.getMessage()));
        }
        return Result.success(fuelOrderRepository.save(order));
    }

    /** Scope is the buyer organization behind the request (as payment events); legacy orders fall back to the company id. */
    private void publishCancelled(FuelOrder order) {
        var organizationId = replenishmentLookup.findByOrderId(order.getId())
                .map(ReplenishmentLookup.ReplenishmentView::organizationId)
                .orElse(order.getCompanyId());
        publicationRegistry.publish("order.cancelled.v1", "FuelOrder", String.valueOf(order.getId()),
                organizationId, order.getVersion(),
                "{\"orderId\":" + order.getId()
                        + ",\"requestId\":" + order.getRequestId()
                        + ",\"organizationId\":" + organizationId
                        + ",\"providerId\":" + order.getProviderId() + "}");
    }
}
