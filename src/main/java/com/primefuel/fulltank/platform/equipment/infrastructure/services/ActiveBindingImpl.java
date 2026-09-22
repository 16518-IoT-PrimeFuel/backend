package com.primefuel.fulltank.platform.equipment.infrastructure.services;

import com.primefuel.fulltank.platform.equipment.api.ActiveBinding;
import com.primefuel.fulltank.platform.equipment.devicebinding.domain.repositories.DeviceBindingRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;

@Component("activeBinding")
public class ActiveBindingImpl implements ActiveBinding {

    private final DeviceBindingRepository repository;

    public ActiveBindingImpl(DeviceBindingRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<BindingSnapshot> activeAt(String deviceId, String channel, Instant instant) {
        if (deviceId == null || channel == null || instant == null) {
            return Optional.empty();
        }
        return repository.findByDeviceAndChannel(deviceId, channel).stream()
                .filter(binding -> binding.covers(instant))
                .max(java.util.Comparator.comparing(
                        com.primefuel.fulltank.platform.equipment.devicebinding.domain.model.aggregates.DeviceBinding::getValidFrom))
                .map(binding -> new BindingSnapshot(
                        binding.getId(),
                        binding.getOrganizationId(),
                        binding.getDeviceId(),
                        binding.getChannel(),
                        binding.getTankId(),
                        binding.getValidFrom(),
                        binding.getValidTo()));
    }
}
