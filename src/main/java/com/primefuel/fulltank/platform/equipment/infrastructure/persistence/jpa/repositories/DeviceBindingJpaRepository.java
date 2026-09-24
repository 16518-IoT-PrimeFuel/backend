package com.primefuel.fulltank.platform.equipment.infrastructure.persistence.jpa.repositories;

import com.primefuel.fulltank.platform.equipment.infrastructure.persistence.jpa.entities.DeviceBindingPersistenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DeviceBindingJpaRepository extends JpaRepository<DeviceBindingPersistenceEntity, Long> {
    List<DeviceBindingPersistenceEntity> findByDeviceIdAndChannelOrderByValidFromAsc(String deviceId, String channel);
}
