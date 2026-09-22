package com.primefuel.fulltank.platform.equipment.infrastructure.persistence.jpa.adapters;

import com.primefuel.fulltank.platform.equipment.domain.model.aggregates.QuarantinedCompanyMapping;
import com.primefuel.fulltank.platform.equipment.domain.repositories.QuarantinedCompanyMappingRepository;
import com.primefuel.fulltank.platform.equipment.infrastructure.persistence.jpa.assemblers.QuarantinedCompanyMappingPersistenceAssembler;
import com.primefuel.fulltank.platform.equipment.infrastructure.persistence.jpa.repositories.QuarantinedCompanyMappingPersistenceRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class QuarantinedCompanyMappingRepositoryImpl implements QuarantinedCompanyMappingRepository {

    private final QuarantinedCompanyMappingPersistenceRepository persistenceRepository;

    public QuarantinedCompanyMappingRepositoryImpl(
            QuarantinedCompanyMappingPersistenceRepository persistenceRepository) {
        this.persistenceRepository = persistenceRepository;
    }

    @Override
    public Optional<QuarantinedCompanyMapping> findByLegacyCompanyId(Long legacyCompanyId) {
        return persistenceRepository.findByLegacyCompanyId(legacyCompanyId)
                .map(QuarantinedCompanyMappingPersistenceAssembler::toDomainFromPersistence);
    }

    @Override
    public QuarantinedCompanyMapping save(QuarantinedCompanyMapping mapping) {
        var entity = QuarantinedCompanyMappingPersistenceAssembler.toPersistenceFromDomain(mapping);
        return QuarantinedCompanyMappingPersistenceAssembler.toDomainFromPersistence(
                persistenceRepository.save(entity));
    }

    @Override
    public long count() {
        return persistenceRepository.count();
    }
}
