package com.primefuel.fulltank.platform.equipment.devicebinding.infrastructure.persistence.jpa.adapters;

import com.primefuel.fulltank.platform.equipment.devicebinding.domain.model.aggregates.DeviceCredential;
import com.primefuel.fulltank.platform.equipment.devicebinding.domain.model.valueobjects.CredentialStatus;
import com.primefuel.fulltank.platform.equipment.devicebinding.domain.repositories.DeviceCredentialRepository;
import com.primefuel.fulltank.platform.equipment.devicebinding.infrastructure.persistence.jpa.assemblers.DeviceCredentialPersistenceAssembler;
import com.primefuel.fulltank.platform.equipment.devicebinding.infrastructure.persistence.jpa.repositories.DeviceCredentialPersistenceRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class DeviceCredentialRepositoryImpl implements DeviceCredentialRepository {

    private final DeviceCredentialPersistenceRepository persistenceRepository;

    public DeviceCredentialRepositoryImpl(DeviceCredentialPersistenceRepository persistenceRepository) {
        this.persistenceRepository = persistenceRepository;
    }

    @Override
    public Optional<DeviceCredential> findActiveByDeviceAndChannel(String deviceId, String channel) {
        return persistenceRepository
                .findByDeviceIdAndChannelAndStatus(deviceId, channel, CredentialStatus.ACTIVE)
                .map(DeviceCredentialPersistenceAssembler::toDomainFromPersistence);
    }

    @Override
    public List<DeviceCredential> findByDeviceAndChannel(String deviceId, String channel) {
        return persistenceRepository.findByDeviceIdAndChannel(deviceId, channel).stream()
                .map(DeviceCredentialPersistenceAssembler::toDomainFromPersistence).toList();
    }

    @Override
    public Optional<DeviceCredential> findByTokenHash(String tokenHash) {
        return persistenceRepository.findByTokenHash(tokenHash)
                .map(DeviceCredentialPersistenceAssembler::toDomainFromPersistence);
    }

    @Override
    public DeviceCredential save(DeviceCredential credential) {
        var entity = DeviceCredentialPersistenceAssembler.toPersistenceFromDomain(credential);
        return DeviceCredentialPersistenceAssembler.toDomainFromPersistence(persistenceRepository.save(entity));
    }
}
