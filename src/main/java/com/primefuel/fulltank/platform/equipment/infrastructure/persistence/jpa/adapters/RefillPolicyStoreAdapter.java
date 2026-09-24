package com.primefuel.fulltank.platform.equipment.infrastructure.persistence.jpa.adapters;

import com.primefuel.fulltank.platform.equipment.application.ports.*;
import com.primefuel.fulltank.platform.equipment.infrastructure.persistence.jpa.entities.RefillEpisodePersistenceEntity;
import com.primefuel.fulltank.platform.equipment.infrastructure.persistence.jpa.entities.RefillPolicyPersistenceEntity;
import com.primefuel.fulltank.platform.equipment.infrastructure.persistence.jpa.repositories.RefillEpisodeJpaRepository;
import com.primefuel.fulltank.platform.equipment.infrastructure.persistence.jpa.repositories.RefillPolicyJpaRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class RefillPolicyStoreAdapter implements RefillPolicyStore {
    private final RefillPolicyJpaRepository policies;
    private final RefillEpisodeJpaRepository episodes;

    public RefillPolicyStoreAdapter(RefillPolicyJpaRepository policies, RefillEpisodeJpaRepository episodes) {
        this.policies = policies;
        this.episodes = episodes;
    }

    @Override
    public RefillPolicyData savePolicy(RefillPolicyData data) {
        var entity = policies.findByTankId(data.tankId()).orElseGet(RefillPolicyPersistenceEntity::new);
        entity.setTankId(data.tankId()); entity.setBuyerCompanyId(data.buyerCompanyId());
        entity.setProviderId(data.providerId()); entity.setFuelProductId(data.fuelProductId());
        entity.setThresholdValue(data.thresholdValue()); entity.setHysteresisValue(data.hysteresisValue());
        entity.setTargetVolume(data.targetVolume()); entity.setDeliveryAddress(data.deliveryAddress());
        entity.setEnabled(data.enabled());
        policies.save(entity);
        return data;
    }

    @Override public Optional<RefillPolicyData> findPolicy(Long tankId) {
        return policies.findByTankId(tankId).map(entity -> new RefillPolicyData(entity.getTankId(), entity.getBuyerCompanyId(),
                entity.getProviderId(), entity.getFuelProductId(), entity.getThresholdValue(), entity.getHysteresisValue(),
                entity.getTargetVolume(), entity.getDeliveryAddress(), entity.isEnabled()));
    }

    @Override public Optional<RefillEpisodeData> findEpisode(String key) {
        return episodes.findByEpisodeKey(key).map(this::toData);
    }

    @Override public Optional<RefillEpisodeData> findOpenEpisode(Long tankId) {
        return episodes.findFirstByTankIdAndStatusIn(tankId, java.util.List.of("OPEN", "REQUESTED")).map(this::toData);
    }

    @Override public RefillEpisodeData saveEpisode(RefillEpisodeData data) {
        var entity = episodes.findByEpisodeKey(data.episodeKey()).orElseGet(RefillEpisodePersistenceEntity::new);
        entity.setEpisodeKey(data.episodeKey()); entity.setTankId(data.tankId()); entity.setStartedAt(data.startedAt());
        entity.setClosedAt(data.closedAt()); entity.setStatus(data.status()); entity.setRequestId(data.requestId());
        return toData(episodes.save(entity));
    }

    private RefillEpisodeData toData(RefillEpisodePersistenceEntity entity) {
        return new RefillEpisodeData(entity.getEpisodeKey(), entity.getTankId(), entity.getStartedAt(),
                entity.getClosedAt(), entity.getStatus(), entity.getRequestId());
    }
}
