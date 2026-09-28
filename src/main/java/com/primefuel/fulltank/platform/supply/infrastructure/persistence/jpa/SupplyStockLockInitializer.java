package com.primefuel.fulltank.platform.supply.infrastructure.persistence.jpa;

import com.primefuel.fulltank.platform.supply.infrastructure.persistence.jpa.entities.SupplyStockLockPersistenceEntity;
import com.primefuel.fulltank.platform.supply.infrastructure.persistence.jpa.repositories.SupplyStockLockPersistenceRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the per-product mutex row in its own transaction so a concurrent creation race cannot poison
 * the surrounding reservation transaction.
 */
@Component
public class SupplyStockLockInitializer {

    private final SupplyStockLockPersistenceRepository lockRepository;

    public SupplyStockLockInitializer(SupplyStockLockPersistenceRepository lockRepository) {
        this.lockRepository = lockRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void ensureExists(Long providerId, Long fuelProductId) {
        if (lockRepository.findByProviderIdAndFuelProductId(providerId, fuelProductId).isPresent()) {
            return;
        }
        try {
            lockRepository.saveAndFlush(new SupplyStockLockPersistenceEntity(providerId, fuelProductId));
        } catch (DataIntegrityViolationException ignored) {
            // another transaction created it first; the unique constraint keeps it single
        }
    }
}
