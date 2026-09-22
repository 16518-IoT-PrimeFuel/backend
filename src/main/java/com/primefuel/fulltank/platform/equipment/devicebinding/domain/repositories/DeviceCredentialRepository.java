package com.primefuel.fulltank.platform.equipment.devicebinding.domain.repositories;

import com.primefuel.fulltank.platform.equipment.devicebinding.domain.model.aggregates.DeviceCredential;

import java.util.List;
import java.util.Optional;

public interface DeviceCredentialRepository {
    Optional<DeviceCredential> findActiveByDeviceAndChannel(String deviceId, String channel);
    List<DeviceCredential> findByDeviceAndChannel(String deviceId, String channel);
    Optional<DeviceCredential> findByTokenHash(String tokenHash);
    DeviceCredential save(DeviceCredential credential);
}
