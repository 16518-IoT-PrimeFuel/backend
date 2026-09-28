package com.primefuel.fulltank.platform.replenishment.infrastructure.persistence.jpa.assemblers;

import com.primefuel.fulltank.platform.replenishment.domain.model.aggregates.RefillEpisode;
import com.primefuel.fulltank.platform.replenishment.domain.model.valueobjects.RefillEpisodeStatus;
import com.primefuel.fulltank.platform.replenishment.infrastructure.persistence.jpa.entities.RefillEpisodePersistenceEntity;

public final class RefillEpisodePersistenceAssembler {

    private RefillEpisodePersistenceAssembler() {
    }

    public static RefillEpisode toDomainFromPersistence(RefillEpisodePersistenceEntity entity) {
        if (entity == null) return null;
        var domain = new RefillEpisode();
        domain.setId(entity.getId());
        domain.setEpisodeKey(entity.getEpisodeKey());
        domain.setTankId(entity.getTankId());
        domain.setOrganizationId(entity.getOrganizationId());
        domain.setPolicyVersion(entity.getPolicyVersion());
        domain.setStatus(entity.getStatus());
        domain.setOpenedAt(entity.getOpenedAt());
        domain.setOpenedLevelPercent(entity.getOpenedLevelPercent());
        domain.setOpenedLevel(entity.getOpenedLevel());
        domain.setTargetLevel(entity.getTargetLevel());
        domain.setRequestedVolume(entity.getRequestedVolume());
        domain.setUnit(entity.getUnit());
        domain.setRequestEmitted(entity.isRequestEmitted());
        domain.setRequestId(entity.getRequestId());
        domain.setClosedAt(entity.getClosedAt());
        domain.setClosedLevelPercent(entity.getClosedLevelPercent());
        domain.setVersion(entity.getVersion());
        return domain;
    }

    public static RefillEpisodePersistenceEntity toPersistenceFromDomain(RefillEpisode domain) {
        if (domain == null) return null;
        var entity = new RefillEpisodePersistenceEntity();
        if (domain.getId() != null) {
            entity.setId(domain.getId());
        }
        entity.setEpisodeKey(domain.getEpisodeKey());
        entity.setTankId(domain.getTankId());
        entity.setOrganizationId(domain.getOrganizationId());
        entity.setPolicyVersion(domain.getPolicyVersion());
        entity.setStatus(domain.getStatus());
        entity.setOpenedAt(domain.getOpenedAt());
        entity.setOpenedLevelPercent(domain.getOpenedLevelPercent());
        entity.setOpenedLevel(domain.getOpenedLevel());
        entity.setTargetLevel(domain.getTargetLevel());
        entity.setRequestedVolume(domain.getRequestedVolume());
        entity.setUnit(domain.getUnit());
        entity.setRequestEmitted(domain.isRequestEmitted());
        entity.setRequestId(domain.getRequestId());
        entity.setClosedAt(domain.getClosedAt());
        entity.setClosedLevelPercent(domain.getClosedLevelPercent());
        entity.setVersion(domain.getVersion());
        // The unique open slot is a persistence concern: only an OPEN episode holds it.
        entity.setOpenSlot(domain.getStatus() == RefillEpisodeStatus.OPEN ? domain.getTankId() : null);
        return entity;
    }
}
