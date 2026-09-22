package com.primefuel.fulltank.platform.supply.infrastructure.persistence.jpa.assemblers;

import com.primefuel.fulltank.platform.supply.domain.model.aggregates.SupplyReservation;
import com.primefuel.fulltank.platform.supply.infrastructure.persistence.jpa.entities.SupplyReservationPersistenceEntity;

public final class SupplyReservationPersistenceAssembler {

    private SupplyReservationPersistenceAssembler() {
    }

    public static SupplyReservation toDomainFromPersistence(SupplyReservationPersistenceEntity entity) {
        if (entity == null) return null;
        var domain = new SupplyReservation();
        domain.setId(entity.getId());
        domain.setProviderId(entity.getProviderId());
        domain.setFuelProductId(entity.getFuelProductId());
        domain.setReference(entity.getReference());
        domain.setQuantity(entity.getQuantity());
        domain.setUnit(entity.getUnit());
        domain.setUnitPrice(entity.getUnitPrice());
        domain.setStatus(entity.getStatus());
        return domain;
    }

    public static SupplyReservationPersistenceEntity toPersistenceFromDomain(SupplyReservation domain) {
        if (domain == null) return null;
        var entity = new SupplyReservationPersistenceEntity();
        if (domain.getId() != null) {
            entity.setId(domain.getId());
        }
        entity.setProviderId(domain.getProviderId());
        entity.setFuelProductId(domain.getFuelProductId());
        entity.setReference(domain.getReference());
        entity.setQuantity(domain.getQuantity());
        entity.setUnit(domain.getUnit());
        entity.setUnitPrice(domain.getUnitPrice());
        entity.setStatus(domain.getStatus());
        return entity;
    }
}
