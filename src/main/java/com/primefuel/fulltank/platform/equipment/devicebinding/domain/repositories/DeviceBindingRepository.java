package com.primefuel.fulltank.platform.equipment.devicebinding.domain.repositories;

import com.primefuel.fulltank.platform.equipment.devicebinding.domain.model.aggregates.DeviceBinding;

import java.util.List;
import java.util.Optional;

public interface DeviceBindingRepository {
    Optional<DeviceBinding> findById(Long id);
    Optional<DeviceBinding> findOpenByDeviceAndChannel(String deviceId, String channel);
    List<DeviceBinding> findByDeviceAndChannel(String deviceId, String channel);
    DeviceBinding save(DeviceBinding binding);
}
