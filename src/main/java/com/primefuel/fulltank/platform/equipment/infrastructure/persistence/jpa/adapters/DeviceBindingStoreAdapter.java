package com.primefuel.fulltank.platform.equipment.infrastructure.persistence.jpa.adapters;

import com.primefuel.fulltank.platform.equipment.application.ports.DeviceBindingData;
import com.primefuel.fulltank.platform.equipment.application.ports.DeviceBindingStore;
import com.primefuel.fulltank.platform.equipment.infrastructure.persistence.jpa.entities.DeviceBindingPersistenceEntity;
import com.primefuel.fulltank.platform.equipment.infrastructure.persistence.jpa.repositories.DeviceBindingJpaRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Component
public class DeviceBindingStoreAdapter implements DeviceBindingStore {
    private final DeviceBindingJpaRepository repository;

    public DeviceBindingStoreAdapter(DeviceBindingJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public DeviceBindingData save(DeviceBindingData binding) {
        var entity = new DeviceBindingPersistenceEntity();
        entity.setDeviceId(binding.deviceId());
        entity.setChannel(binding.channel());
        entity.setTankId(binding.tankId());
        entity.setBuyerCompanyId(binding.buyerCompanyId());
        entity.setCredentialHash(binding.credentialHash());
        entity.setValidFrom(binding.validFrom());
        entity.setValidUntil(binding.validUntil());
        entity.setStatus(binding.status());
        var saved = repository.save(entity);
        return toData(saved);
    }

    @Override
    public Optional<DeviceBindingData> findById(Long id) {
        return repository.findById(id).map(this::toData);
    }

    @Override
    public List<DeviceBindingData> findByDeviceAndChannel(String deviceId, String channel) {
        return repository.findByDeviceIdAndChannelOrderByValidFromAsc(deviceId, channel)
                .stream().map(this::toData).toList();
    }

    @Override
    public void revoke(Long id, Instant revokedAt) {
        repository.findById(id).ifPresent(entity -> {
            entity.setStatus("REVOKED");
            entity.setValidUntil(revokedAt);
            repository.save(entity);
        });
    }

    private DeviceBindingData toData(DeviceBindingPersistenceEntity entity) {
        return new DeviceBindingData(entity.getId(), entity.getDeviceId(), entity.getChannel(), entity.getTankId(),
                entity.getBuyerCompanyId(), entity.getCredentialHash(), entity.getValidFrom(), entity.getValidUntil(),
                entity.getStatus());
    }
}
