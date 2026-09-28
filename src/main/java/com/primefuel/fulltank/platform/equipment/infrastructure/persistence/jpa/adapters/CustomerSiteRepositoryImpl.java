package com.primefuel.fulltank.platform.equipment.infrastructure.persistence.jpa.adapters;

import com.primefuel.fulltank.platform.equipment.domain.model.aggregates.CustomerSite;
import com.primefuel.fulltank.platform.equipment.domain.repositories.CustomerSiteRepository;
import com.primefuel.fulltank.platform.equipment.infrastructure.persistence.jpa.assemblers.CustomerSitePersistenceAssembler;
import com.primefuel.fulltank.platform.equipment.infrastructure.persistence.jpa.repositories.CustomerSitePersistenceRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class CustomerSiteRepositoryImpl implements CustomerSiteRepository {

    private final CustomerSitePersistenceRepository persistenceRepository;

    public CustomerSiteRepositoryImpl(CustomerSitePersistenceRepository persistenceRepository) {
        this.persistenceRepository = persistenceRepository;
    }

    @Override
    public Optional<CustomerSite> findById(Long id) {
        return persistenceRepository.findById(id).map(CustomerSitePersistenceAssembler::toDomainFromPersistence);
    }

    @Override
    public List<CustomerSite> findByCustomerAccountId(Long customerAccountId) {
        return persistenceRepository.findByCustomerAccountId(customerAccountId).stream()
                .map(CustomerSitePersistenceAssembler::toDomainFromPersistence).toList();
    }

    @Override
    public List<CustomerSite> findByOrganizationId(Long organizationId) {
        return persistenceRepository.findByOrganizationId(organizationId).stream()
                .map(CustomerSitePersistenceAssembler::toDomainFromPersistence).toList();
    }

    @Override
    public CustomerSite save(CustomerSite customerSite) {
        var entity = CustomerSitePersistenceAssembler.toPersistenceFromDomain(customerSite);
        return CustomerSitePersistenceAssembler.toDomainFromPersistence(persistenceRepository.save(entity));
    }
}
