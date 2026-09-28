package com.primefuel.fulltank.platform.equipment.devicebinding.infrastructure.persistence.jpa.adapters;

import com.primefuel.fulltank.platform.equipment.devicebinding.domain.model.aggregates.DeviceBinding;
import com.primefuel.fulltank.platform.equipment.devicebinding.domain.repositories.DeviceBindingRepository;
import com.primefuel.fulltank.platform.equipment.devicebinding.infrastructure.persistence.jpa.assemblers.DeviceBindingPersistenceAssembler;
import com.primefuel.fulltank.platform.equipment.devicebinding.infrastructure.persistence.jpa.repositories.DeviceBindingPersistenceRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class DeviceBindingRepositoryImpl implements DeviceBindingRepository {

    private final DeviceBindingPersistenceRepository persistenceRepository;

    public DeviceBindingRepositoryImpl(DeviceBindingPersistenceRepository persistenceRepository) {
        this.persistenceRepository = persistenceRepository;
    }

    @Override
    public Optional<DeviceBinding> findById(Long id) {
        return persistenceRepository.findById(id).map(DeviceBindingPersistenceAssembler::toDomainFromPersistence);
    }

    @Override
    public Optional<DeviceBinding> findOpenByDeviceAndChannel(String deviceId, String channel) {
        return persistenceRepository
                .findByDeviceIdAndChannelAndActiveSlot(deviceId, channel, DeviceBinding.OPEN_SLOT)
                .map(DeviceBindingPersistenceAssembler::toDomainFromPersistence);
    }

    @Override
    public List<DeviceBinding> findByDeviceAndChannel(String deviceId, String channel) {
        return persistenceRepository.findByDeviceIdAndChannelOrderByValidFromAsc(deviceId, channel).stream()
                .map(DeviceBindingPersistenceAssembler::toDomainFromPersistence).toList();
    }

    @Override
    public DeviceBinding save(DeviceBinding binding) {
        var entity = DeviceBindingPersistenceAssembler.toPersistenceFromDomain(binding);
        return DeviceBindingPersistenceAssembler.toDomainFromPersistence(persistenceRepository.saveAndFlush(entity));
    }
}
