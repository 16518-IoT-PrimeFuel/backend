package com.primefuel.fulltank.platform.equipment.devicebinding.application.internal.queryservices;

import com.primefuel.fulltank.platform.equipment.devicebinding.application.queryservices.DeviceBindingQueryService;
import com.primefuel.fulltank.platform.equipment.devicebinding.domain.model.aggregates.DeviceBinding;
import com.primefuel.fulltank.platform.equipment.devicebinding.domain.model.queries.GetActiveBindingQuery;
import com.primefuel.fulltank.platform.equipment.devicebinding.domain.model.queries.GetBindingsByDeviceQuery;
import com.primefuel.fulltank.platform.equipment.devicebinding.domain.repositories.DeviceBindingRepository;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
public class DeviceBindingQueryServiceImpl implements DeviceBindingQueryService {

    private final DeviceBindingRepository repository;

    public DeviceBindingQueryServiceImpl(DeviceBindingRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<DeviceBinding> handle(GetActiveBindingQuery query) {
        return repository.findByDeviceAndChannel(query.deviceId(), query.channel()).stream()
                .filter(binding -> binding.covers(query.instant()))
                .max(Comparator.comparing(DeviceBinding::getValidFrom));
    }

    @Override
    public List<DeviceBinding> handle(GetBindingsByDeviceQuery query) {
        return repository.findByDeviceAndChannel(query.deviceId(), query.channel()).stream()
                .sorted(Comparator.comparing(DeviceBinding::getValidFrom))
                .toList();
    }
}
