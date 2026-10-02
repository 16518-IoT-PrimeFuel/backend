package com.primefuel.fulltank.platform.equipment.infrastructure.persistence.jpa.repositories;

import com.primefuel.fulltank.platform.equipment.infrastructure.persistence.jpa.entities.ProviderBuyerLinkPersistenceEntity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProviderBuyerLinkPersistenceRepository
        extends JpaRepository<ProviderBuyerLinkPersistenceEntity, Long> {
    List<ProviderBuyerLinkPersistenceEntity> findByProviderId(Long providerId);

    Optional<ProviderBuyerLinkPersistenceEntity> findByProviderIdAndBuyerCompanyId(
            Long providerId, Long buyerCompanyId);
}
