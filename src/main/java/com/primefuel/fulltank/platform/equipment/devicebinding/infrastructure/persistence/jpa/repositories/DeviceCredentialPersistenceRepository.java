package com.primefuel.fulltank.platform.equipment.devicebinding.infrastructure.persistence.jpa.repositories;

import com.primefuel.fulltank.platform.equipment.devicebinding.domain.model.valueobjects.CredentialStatus;
import com.primefuel.fulltank.platform.equipment.devicebinding.infrastructure.persistence.jpa.entities.DeviceCredentialPersistenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeviceCredentialPersistenceRepository
        extends JpaRepository<DeviceCredentialPersistenceEntity, Long> {

    Optional<DeviceCredentialPersistenceEntity> findByDeviceIdAndChannelAndStatus(
            String deviceId, String channel, CredentialStatus status);

    List<DeviceCredentialPersistenceEntity> findByDeviceIdAndChannel(String deviceId, String channel);

    Optional<DeviceCredentialPersistenceEntity> findByTokenHash(String tokenHash);
}
