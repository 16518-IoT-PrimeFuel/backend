package com.primefuel.fulltank.platform.equipment.devicebinding.infrastructure.persistence.jpa.repositories;

import com.primefuel.fulltank.platform.equipment.devicebinding.infrastructure.persistence.jpa.entities.DeviceBindingPersistenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeviceBindingPersistenceRepository extends JpaRepository<DeviceBindingPersistenceEntity, Long> {

    List<DeviceBindingPersistenceEntity> findByDeviceIdAndChannelOrderByValidFromAsc(String deviceId, String channel);

    Optional<DeviceBindingPersistenceEntity> findByDeviceIdAndChannelAndActiveSlot(
            String deviceId, String channel, Integer activeSlot);
}
