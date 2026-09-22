package com.primefuel.fulltank.platform.supply.interfaces.rest.transform;

import com.primefuel.fulltank.platform.supply.api.SupplyCatalog;
import com.primefuel.fulltank.platform.supply.interfaces.rest.resources.ProductResource;

public final class ProductResourceFromSnapshotAssembler {

    private ProductResourceFromSnapshotAssembler() {
    }

    public static ProductResource toResourceFromSnapshot(SupplyCatalog.SupplySnapshot snapshot) {
        return new ProductResource(
                snapshot.fuelProductId(),
                snapshot.name(),
                snapshot.fuelType(),
                snapshot.unit(),
                snapshot.pricePerUnit(),
                snapshot.stock(),
                snapshot.active());
    }
}
