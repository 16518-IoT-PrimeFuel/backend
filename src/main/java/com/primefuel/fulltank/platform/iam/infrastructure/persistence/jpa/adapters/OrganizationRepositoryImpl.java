package com.primefuel.fulltank.platform.iam.infrastructure.persistence.jpa.adapters;

import com.primefuel.fulltank.platform.iam.domain.model.aggregates.Organization;
import com.primefuel.fulltank.platform.iam.domain.repositories.OrganizationRepository;
import com.primefuel.fulltank.platform.iam.infrastructure.persistence.jpa.assemblers.OrganizationPersistenceAssembler;
import com.primefuel.fulltank.platform.iam.infrastructure.persistence.jpa.repositories.OrganizationPersistenceRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class OrganizationRepositoryImpl implements OrganizationRepository {

    private final OrganizationPersistenceRepository organizationPersistenceRepository;

    public OrganizationRepositoryImpl(OrganizationPersistenceRepository organizationPersistenceRepository) {
        this.organizationPersistenceRepository = organizationPersistenceRepository;
    }

    @Override
    public Optional<Organization> findById(Long id) {
        return organizationPersistenceRepository.findById(id)
                .map(OrganizationPersistenceAssembler::toDomainFromPersistence);
    }

    @Override
    public Optional<Organization> findByRuc(String ruc) {
        return organizationPersistenceRepository.findByRuc(ruc)
                .map(OrganizationPersistenceAssembler::toDomainFromPersistence);
    }

    @Override
    public Organization save(Organization organization) {
        var entity = OrganizationPersistenceAssembler.toPersistenceFromDomain(organization);
        return OrganizationPersistenceAssembler.toDomainFromPersistence(
                organizationPersistenceRepository.save(entity));
    }

    @Override
    public boolean existsById(Long id) {
        return organizationPersistenceRepository.existsById(id);
    }
}
