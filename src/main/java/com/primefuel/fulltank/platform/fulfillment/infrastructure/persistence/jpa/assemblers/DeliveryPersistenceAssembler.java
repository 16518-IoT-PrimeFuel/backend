package com.primefuel.fulltank.platform.fulfillment.infrastructure.persistence.jpa.assemblers;

import com.primefuel.fulltank.platform.fulfillment.domain.model.aggregates.Delivery;
import com.primefuel.fulltank.platform.fulfillment.infrastructure.persistence.jpa.entities.DeliveryPersistenceEntity;

public final class DeliveryPersistenceAssembler {

    private DeliveryPersistenceAssembler() {
    }

    public static Delivery toDomainFromPersistence(DeliveryPersistenceEntity entity) {
        if (entity == null) return null;
        var domain = new Delivery();
        domain.setId(entity.getId());
        domain.setOrderId(entity.getOrderId());
        domain.setProviderId(entity.getProviderId());
        domain.setDriverId(entity.getDriverId());
        domain.setVehicleId(entity.getVehicleId());
        domain.setStatus(entity.getStatus());
        domain.setScheduledDate(entity.getScheduledDate());
        domain.setDispatchedAt(entity.getDispatchedAt());
        domain.setDeliveredAt(entity.getDeliveredAt());
        domain.setNotes(entity.getNotes());
        domain.setPhysicalState(entity.getPhysicalState());
        domain.setStartedAt(entity.getStartedAt());
        domain.setArrivedAt(entity.getArrivedAt());
        domain.setDeliveringAt(entity.getDeliveringAt());
        domain.setRequestedVolume(entity.getRequestedVolume());
        domain.setDeliveredVolume(entity.getDeliveredVolume());
        domain.setVersion(entity.getVersion());
        return domain;
    }

    public static DeliveryPersistenceEntity toPersistenceFromDomain(Delivery domain) {
        if (domain == null) return null;
        var entity = new DeliveryPersistenceEntity();
        if (domain.getId() != null) {
            entity.setId(domain.getId());
        }
        entity.setOrderId(domain.getOrderId());
        entity.setProviderId(domain.getProviderId());
        entity.setDriverId(domain.getDriverId());
        entity.setVehicleId(domain.getVehicleId());
        entity.setStatus(domain.getStatus());
        entity.setScheduledDate(domain.getScheduledDate());
        entity.setDispatchedAt(domain.getDispatchedAt());
        entity.setDeliveredAt(domain.getDeliveredAt());
        entity.setNotes(domain.getNotes());
        entity.setPhysicalState(domain.getPhysicalState());
        entity.setStartedAt(domain.getStartedAt());
        entity.setArrivedAt(domain.getArrivedAt());
        entity.setDeliveringAt(domain.getDeliveringAt());
        entity.setRequestedVolume(domain.getRequestedVolume());
        entity.setDeliveredVolume(domain.getDeliveredVolume());
        entity.setVersion(domain.getVersion());
        return entity;
    }
}
