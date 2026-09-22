package com.primefuel.fulltank.platform.replenishment.infrastructure.persistence.jpa.assemblers;

import com.primefuel.fulltank.platform.replenishment.domain.model.aggregates.ReplenishmentRequest;
import com.primefuel.fulltank.platform.replenishment.infrastructure.persistence.jpa.entities.ReplenishmentRequestPersistenceEntity;

public final class ReplenishmentRequestPersistenceAssembler {

    private ReplenishmentRequestPersistenceAssembler() {
    }

    public static ReplenishmentRequest toDomainFromPersistence(ReplenishmentRequestPersistenceEntity entity) {
        if (entity == null) return null;
        var domain = new ReplenishmentRequest();
        domain.setId(entity.getId());
        domain.setOrganizationId(entity.getOrganizationId());
        domain.setCustomerAccountId(entity.getCustomerAccountId());
        domain.setTankId(entity.getTankId());
        domain.setProviderId(entity.getProviderId());
        domain.setFuelProductId(entity.getFuelProductId());
        domain.setQuantity(entity.getQuantity());
        domain.setUnit(entity.getUnit());
        domain.setUnitPrice(entity.getUnitPrice());
        domain.setStatus(entity.getStatus());
        domain.setSource(entity.getSource());
        domain.setEpisodeKey(entity.getEpisodeKey());
        domain.setRejectionReason(entity.getRejectionReason());
        domain.setOrderId(entity.getOrderId());
        domain.setAcceptanceConsumed(entity.isAcceptanceConsumed());
        domain.setVersion(entity.getVersion());
        return domain;
    }

    public static ReplenishmentRequestPersistenceEntity toPersistenceFromDomain(ReplenishmentRequest domain) {
        if (domain == null) return null;
        var entity = new ReplenishmentRequestPersistenceEntity();
        if (domain.getId() != null) {
            entity.setId(domain.getId());
        }
        entity.setOrganizationId(domain.getOrganizationId());
        entity.setCustomerAccountId(domain.getCustomerAccountId());
        entity.setTankId(domain.getTankId());
        entity.setProviderId(domain.getProviderId());
        entity.setFuelProductId(domain.getFuelProductId());
        entity.setQuantity(domain.getQuantity());
        entity.setUnit(domain.getUnit());
        entity.setUnitPrice(domain.getUnitPrice());
        entity.setStatus(domain.getStatus());
        entity.setSource(domain.getSource());
        entity.setEpisodeKey(domain.getEpisodeKey());
        entity.setRejectionReason(domain.getRejectionReason());
        entity.setOrderId(domain.getOrderId());
        entity.setAcceptanceConsumed(domain.isAcceptanceConsumed());
        entity.setVersion(domain.getVersion());
        return entity;
    }
}
