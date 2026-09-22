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
import com.primefuel.fulltank.platform.ordering.domain.repositories.FuelOrderRepository;
import com.primefuel.fulltank.platform.shared.application.result.ApplicationError;
import com.primefuel.fulltank.platform.shared.application.result.Result;
import org.springframework.stereotype.Service;

@Service
public class FuelOrderCommandServiceImpl implements FuelOrderCommandService {

    private final FuelOrderRepository fuelOrderRepository;
    private final FuelProductQueryService fuelProductQueryService;
    private final EquipmentQueryService equipmentQueryService;

    public FuelOrderCommandServiceImpl(FuelOrderRepository fuelOrderRepository,
                                       FuelProductQueryService fuelProductQueryService,
                                       EquipmentQueryService equipmentQueryService) {
        this.fuelOrderRepository = fuelOrderRepository;
        this.fuelProductQueryService = fuelProductQueryService;
        this.equipmentQueryService = equipmentQueryService;
    }

    @Override
    public Result<FuelOrder, ApplicationError> handle(CreateFuelOrderCommand command) {
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
    public Result<FuelOrder, ApplicationError> handle(ConfirmFuelOrderCommand command) {
        var existing = fuelOrderRepository.findById(command.orderId());
        if (existing.isEmpty()) {
            return Result.failure(ApplicationError.notFound("FuelOrder", command.orderId().toString()));
        }
        var order = existing.get();
        order.confirm();
        return Result.success(fuelOrderRepository.save(order));
    }

    @Override
    public Result<FuelOrder, ApplicationError> handle(CancelFuelOrderCommand command) {
        var existing = fuelOrderRepository.findById(command.orderId());
        if (existing.isEmpty()) {
            return Result.failure(ApplicationError.notFound("FuelOrder", command.orderId().toString()));
        }
        var order = existing.get();
        order.cancel();
        return Result.success(fuelOrderRepository.save(order));
    }
}
