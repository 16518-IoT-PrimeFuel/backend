package com.primefuel.fulltank.platform.inventory.interfaces.rest.transform;

import com.primefuel.fulltank.platform.shared.domain.model.valueobjects.Unit;
import com.primefuel.fulltank.platform.inventory.domain.model.aggregates.FuelProduct;
import com.primefuel.fulltank.platform.inventory.interfaces.rest.resources.FuelProductResource;

public final class FuelProductResourceFromEntityAssembler {

    private FuelProductResourceFromEntityAssembler() {
    }

    public static FuelProductResource toResourceFromEntity(FuelProduct fuelProduct) {
        return new FuelProductResource(fuelProduct.getId(), fuelProduct.getName(),
                fuelProduct.getFuelType(), fuelProduct.getPricePerUnit(), Unit.fromCode(fuelProduct.getUnit()).name(),
                fuelProduct.getAvailableStock(), fuelProduct.getCapacity(), fuelProduct.getProviderId(),
                fuelProduct.getActive() == null || fuelProduct.getActive());
    }
}
