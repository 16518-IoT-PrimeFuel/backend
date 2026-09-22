package com.primefuel.fulltank.platform.equipment.devicebinding.application.queryservices;

import com.primefuel.fulltank.platform.equipment.devicebinding.domain.model.aggregates.DeviceBinding;
import com.primefuel.fulltank.platform.equipment.devicebinding.domain.model.queries.GetActiveBindingQuery;
import com.primefuel.fulltank.platform.equipment.devicebinding.domain.model.queries.GetBindingsByDeviceQuery;

import java.util.List;
import java.util.Optional;

public interface DeviceBindingQueryService {
    Optional<DeviceBinding> handle(GetActiveBindingQuery query);
    List<DeviceBinding> handle(GetBindingsByDeviceQuery query);
}
