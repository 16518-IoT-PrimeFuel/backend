package com.primefuel.fulltank.platform.equipment.infrastructure.persistence.jpa.adapters;

import com.primefuel.fulltank.platform.equipment.domain.model.aggregates.CustomerAccount;
import com.primefuel.fulltank.platform.equipment.domain.repositories.CustomerAccountRepository;
import com.primefuel.fulltank.platform.equipment.infrastructure.persistence.jpa.assemblers.CustomerAccountPersistenceAssembler;
import com.primefuel.fulltank.platform.equipment.infrastructure.persistence.jpa.repositories.CustomerAccountPersistenceRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class CustomerAccountRepositoryImpl implements CustomerAccountRepository {

    private final CustomerAccountPersistenceRepository persistenceRepository;

    public CustomerAccountRepositoryImpl(CustomerAccountPersistenceRepository persistenceRepository) {
        this.persistenceRepository = persistenceRepository;
    }

    @Override
    public Optional<CustomerAccount> findById(Long id) {
        return persistenceRepository.findById(id).map(CustomerAccountPersistenceAssembler::toDomainFromPersistence);
    }

    @Override
    public List<CustomerAccount> findByOrganizationId(Long organizationId) {
        return persistenceRepository.findByOrganizationId(organizationId).stream()
                .map(CustomerAccountPersistenceAssembler::toDomainFromPersistence).toList();
    }

    @Override
    public Optional<CustomerAccount> findByLegacyCompanyId(Long legacyCompanyId) {
        return persistenceRepository.findByLegacyCompanyId(legacyCompanyId)
                .map(CustomerAccountPersistenceAssembler::toDomainFromPersistence);
    }

    @Override
    public Optional<CustomerAccount> findByOrganizationIdAndRuc(Long organizationId, String ruc) {
        return persistenceRepository.findByOrganizationIdAndRuc(organizationId, ruc)
                .map(CustomerAccountPersistenceAssembler::toDomainFromPersistence);
    }

    @Override
    public CustomerAccount save(CustomerAccount customerAccount) {
        var entity = CustomerAccountPersistenceAssembler.toPersistenceFromDomain(customerAccount);
        return CustomerAccountPersistenceAssembler.toDomainFromPersistence(persistenceRepository.save(entity));
    }

    @Override
    public long count() {
        return persistenceRepository.count();
    }
}
