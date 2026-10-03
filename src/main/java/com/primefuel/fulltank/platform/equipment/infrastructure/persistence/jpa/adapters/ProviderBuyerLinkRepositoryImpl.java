package com.primefuel.fulltank.platform.equipment.infrastructure.persistence.jpa.adapters;

import com.primefuel.fulltank.platform.equipment.domain.model.aggregates.ProviderBuyerLink;
import com.primefuel.fulltank.platform.equipment.domain.repositories.ProviderBuyerLinkRepository;
import com.primefuel.fulltank.platform.equipment.infrastructure.persistence.jpa.assemblers.ProviderBuyerLinkPersistenceAssembler;
import com.primefuel.fulltank.platform.equipment.infrastructure.persistence.jpa.repositories.ProviderBuyerLinkPersistenceRepository;

import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class ProviderBuyerLinkRepositoryImpl implements ProviderBuyerLinkRepository {
    private final ProviderBuyerLinkPersistenceRepository repository;

    public ProviderBuyerLinkRepositoryImpl(ProviderBuyerLinkPersistenceRepository repository) {
        this.repository = repository;
    }

    public List<ProviderBuyerLink> findByProviderId(Long providerId) {
        return repository.findByProviderId(providerId).stream()
                .map(ProviderBuyerLinkPersistenceAssembler::toDomainFromPersistence)
                .toList();
    }

    public Optional<ProviderBuyerLink> findByProviderIdAndBuyerCompanyId(
            Long providerId, Long buyerCompanyId) {
        return repository
                .findByProviderIdAndBuyerCompanyId(providerId, buyerCompanyId)
                .map(ProviderBuyerLinkPersistenceAssembler::toDomainFromPersistence);
    }

    public ProviderBuyerLink save(ProviderBuyerLink link) {
        return ProviderBuyerLinkPersistenceAssembler.toDomainFromPersistence(
                repository.saveAndFlush(
                        ProviderBuyerLinkPersistenceAssembler.toPersistenceFromDomain(link)));
    }
}
