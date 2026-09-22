package com.primefuel.fulltank.platform.supply.infrastructure.inventory;

import com.primefuel.fulltank.platform.inventory.application.queryservices.FuelProductQueryService;
import com.primefuel.fulltank.platform.inventory.domain.model.queries.GetFuelProductByIdQuery;
import com.primefuel.fulltank.platform.inventory.domain.model.queries.GetFuelProductsByProviderIdQuery;
import com.primefuel.fulltank.platform.supply.api.SupplyCatalog;
import com.primefuel.fulltank.platform.shared.domain.model.valueobjects.Unit;
import com.primefuel.fulltank.platform.shared.domain.model.valueobjects.Volume;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component("inventorySupplyCatalog")
public class InventorySupplyCatalog implements SupplyCatalog {

    private final FuelProductQueryService fuelProductQueryService;

    public InventorySupplyCatalog(FuelProductQueryService fuelProductQueryService) {
        this.fuelProductQueryService = fuelProductQueryService;
    }

    @Override
    public Optional<SupplySnapshot> findForTenant(Long providerId, Long fuelProductId) {
        if (providerId == null || fuelProductId == null) {
            return Optional.empty();
        }
        return fuelProductQueryService.handle(new GetFuelProductByIdQuery(fuelProductId))
                .filter(product -> providerId.equals(product.getProviderId()))
                .map(this::toSnapshot);
    }

    @Override
    public List<SupplySnapshot> listForTenant(Long providerId, boolean onlyActive) {
        if (providerId == null) {
            return List.of();
        }
        return fuelProductQueryService.handle(new GetFuelProductsByProviderIdQuery(providerId)).stream()
                .filter(product -> !onlyActive || Boolean.TRUE.equals(product.getActive()))
                .map(this::toSnapshot)
                .toList();
    }

    private SupplySnapshot toSnapshot(com.primefuel.fulltank.platform.inventory.domain.model.aggregates.FuelProduct product) {
        var unit = Unit.fromCode(product.getUnit());
        var stock = Volume.of(product.getAvailableStock() == null ? 0.0 : product.getAvailableStock(), unit);
        return new SupplySnapshot(
                product.getId(),
                product.getProviderId(),
                product.getName(),
                product.getFuelType() == null ? null : product.getFuelType().name(),
                unit.name(),
                product.getPricePerUnit() == null ? 0.0 : product.getPricePerUnit(),
                stock.amount(),
                Boolean.TRUE.equals(product.getActive()));
    }
}
