package com.primefuel.fulltank.platform.equipment.domain.repositories;

import com.primefuel.fulltank.platform.equipment.domain.model.aggregates.TankConfiguration;

import java.util.List;

public interface TankConfigurationRepository {
    List<TankConfiguration> findByTankId(Long tankId);
    TankConfiguration save(TankConfiguration configuration);
}
