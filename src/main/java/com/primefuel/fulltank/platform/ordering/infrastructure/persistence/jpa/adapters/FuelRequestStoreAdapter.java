package com.primefuel.fulltank.platform.ordering.infrastructure.persistence.jpa.adapters;

import com.primefuel.fulltank.platform.ordering.application.ports.FuelRequestData;
import com.primefuel.fulltank.platform.ordering.application.ports.FuelRequestStore;
import com.primefuel.fulltank.platform.ordering.infrastructure.persistence.jpa.entities.FuelRequestPersistenceEntity;
import com.primefuel.fulltank.platform.ordering.infrastructure.persistence.jpa.repositories.FuelRequestPersistenceRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class FuelRequestStoreAdapter implements FuelRequestStore {

    private final FuelRequestPersistenceRepository repository;

    public FuelRequestStoreAdapter(FuelRequestPersistenceRepository repository) {
        this.repository = repository;
    }

    @Override
    public FuelRequestData save(FuelRequestData request) {
        return toData(repository.save(toEntity(request)));
    }

    @Override
    public List<FuelRequestData> findAll() {
        return repository.findAll().stream().map(FuelRequestStoreAdapter::toData).toList();
    }

    @Override
    public List<FuelRequestData> findByBuyerCompanyId(Long buyerCompanyId) {
        return repository.findByBuyerCompanyId(buyerCompanyId).stream()
                .map(FuelRequestStoreAdapter::toData).toList();
    }

    @Override
    public List<FuelRequestData> findByProviderId(Long providerId) {
        return repository.findByProviderId(providerId).stream()
                .map(FuelRequestStoreAdapter::toData).toList();
    }

    @Override
    public Optional<FuelRequestData> findById(Long requestId) {
        return repository.findById(requestId).map(FuelRequestStoreAdapter::toData);
    }

    private static FuelRequestPersistenceEntity toEntity(FuelRequestData request) {
        var entity = new FuelRequestPersistenceEntity();
        entity.setId(request.id());
        entity.setBuyerCompanyId(request.buyerCompanyId());
        entity.setProviderId(request.providerId());
        entity.setEquipmentId(request.equipmentId());
        entity.setFuelProductId(request.fuelProductId());
        entity.setFuelType(request.fuelType());
        entity.setProductName(request.productName());
        entity.setQuantity(request.quantity());
        entity.setUnit(request.unit());
        entity.setUnitPrice(request.unitPrice());
        entity.setDeliveryAddress(request.deliveryAddress());
        entity.setDeliveryDate(request.deliveryDate());
        entity.setStatus(request.status());
        entity.setSource(request.source());
        entity.setRejectionReason(request.rejectionReason());
        return entity;
    }

    private static FuelRequestData toData(FuelRequestPersistenceEntity entity) {
        return new FuelRequestData(entity.getId(), entity.getBuyerCompanyId(), entity.getProviderId(),
                entity.getEquipmentId(), entity.getFuelProductId(), entity.getFuelType(), entity.getProductName(),
                entity.getQuantity(), entity.getUnit(), entity.getUnitPrice(), entity.getDeliveryAddress(),
                entity.getDeliveryDate(), entity.getStatus(), entity.getSource(), entity.getRejectionReason(),
                entity.getCreatedAt(), entity.getUpdatedAt());
    }
}
