package com.primefuel.fulltank.platform.safety.infrastructure.persistence.jpa.adapters;

import com.primefuel.fulltank.platform.safety.domain.model.entities.SafetyDecision;
import com.primefuel.fulltank.platform.safety.domain.repositories.SafetyDecisionRepository;
import com.primefuel.fulltank.platform.safety.infrastructure.persistence.jpa.assemblers.SafetyDecisionPersistenceAssembler;
import com.primefuel.fulltank.platform.safety.infrastructure.persistence.jpa.repositories.SafetyDecisionPersistenceRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class SafetyDecisionRepositoryImpl implements SafetyDecisionRepository {

    private final SafetyDecisionPersistenceRepository persistenceRepository;

    public SafetyDecisionRepositoryImpl(SafetyDecisionPersistenceRepository persistenceRepository) {
        this.persistenceRepository = persistenceRepository;
    }

    @Override
    public SafetyDecision save(SafetyDecision decision) {
        return SafetyDecisionPersistenceAssembler.toDomain(
                persistenceRepository.save(SafetyDecisionPersistenceAssembler.toPersistence(decision)));
    }

    @Override
    public List<SafetyDecision> findByDeliveryId(Long deliveryId) {
        return persistenceRepository.findByDeliveryIdOrderByEvaluatedAtAsc(deliveryId).stream()
                .map(SafetyDecisionPersistenceAssembler::toDomain)
                .toList();
    }

    @Override
    public long countByDeliveryId(Long deliveryId) {
        return persistenceRepository.countByDeliveryId(deliveryId);
    }
}
