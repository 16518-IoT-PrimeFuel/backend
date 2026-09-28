package com.primefuel.fulltank.platform.replenishment.infrastructure.persistence.jpa.adapters;

import com.primefuel.fulltank.platform.replenishment.domain.model.aggregates.RefillPolicy;
import com.primefuel.fulltank.platform.replenishment.domain.repositories.RefillPolicyRepository;
import com.primefuel.fulltank.platform.replenishment.infrastructure.persistence.jpa.assemblers.RefillPolicyPersistenceAssembler;
import com.primefuel.fulltank.platform.replenishment.infrastructure.persistence.jpa.repositories.RefillPolicyPersistenceRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class RefillPolicyRepositoryImpl implements RefillPolicyRepository {

    private final RefillPolicyPersistenceRepository persistenceRepository;

    public RefillPolicyRepositoryImpl(RefillPolicyPersistenceRepository persistenceRepository) {
        this.persistenceRepository = persistenceRepository;
    }

    @Override
    public Optional<RefillPolicy> findByTankId(Long tankId) {
        return persistenceRepository.findByTankId(tankId)
                .map(RefillPolicyPersistenceAssembler::toDomainFromPersistence);
    }

    @Override
    public RefillPolicy save(RefillPolicy policy) {
        var entity = RefillPolicyPersistenceAssembler.toPersistenceFromDomain(policy);
        return RefillPolicyPersistenceAssembler.toDomainFromPersistence(persistenceRepository.save(entity));
    }
}
