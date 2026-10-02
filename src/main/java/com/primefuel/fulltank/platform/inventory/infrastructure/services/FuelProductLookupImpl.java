package com.primefuel.fulltank.platform.inventory.infrastructure.services;

import com.primefuel.fulltank.platform.inventory.api.FuelProductLookup;
import com.primefuel.fulltank.platform.inventory.domain.repositories.FuelProductRepository;

import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class FuelProductLookupImpl implements FuelProductLookup {
    private final FuelProductRepository products;

    public FuelProductLookupImpl(FuelProductRepository products) {
        this.products = products;
    }

    public Optional<ProductSnapshot> findById(Long id) {
        if (id == null) return Optional.empty();
        return products.findById(id)
                .map(
                        p ->
                                new ProductSnapshot(
                                        p.getId(),
                                        p.getProviderId(),
                                        p.getUnit(),
                                        p.getFuelType().name(),
                                        Boolean.TRUE.equals(p.getActive())));
    }
}
