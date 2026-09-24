package com.primefuel.fulltank.platform.equipment.application.ports;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface DeviceBindingStore {
    DeviceBindingData save(DeviceBindingData binding);

    Optional<DeviceBindingData> findById(Long id);

    List<DeviceBindingData> findByDeviceAndChannel(String deviceId, String channel);

    void revoke(Long id, Instant revokedAt);
}
