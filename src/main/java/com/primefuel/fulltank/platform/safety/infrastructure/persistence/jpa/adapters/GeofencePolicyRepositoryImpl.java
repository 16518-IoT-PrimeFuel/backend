package com.primefuel.fulltank.platform.safety.infrastructure.persistence.jpa.adapters;

import com.primefuel.fulltank.platform.safety.domain.model.aggregates.GeofencePolicy;
import com.primefuel.fulltank.platform.safety.domain.repositories.GeofencePolicyRepository;
import com.primefuel.fulltank.platform.safety.infrastructure.persistence.jpa.assemblers.GeofencePolicyPersistenceAssembler;
import com.primefuel.fulltank.platform.safety.infrastructure.persistence.jpa.repositories.GeofencePolicyPersistenceRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class GeofencePolicyRepositoryImpl implements GeofencePolicyRepository {

    private final GeofencePolicyPersistenceRepository persistenceRepository;

    public GeofencePolicyRepositoryImpl(GeofencePolicyPersistenceRepository persistenceRepository) {
        this.persistenceRepository = persistenceRepository;
    }

    @Override
    public GeofencePolicy save(GeofencePolicy policy) {
        // saveAndFlush so the (delivery_id, policy_version) uniqueness of the append-only versioning surfaces
        // inside the caller's transaction instead of at commit time.
        return GeofencePolicyPersistenceAssembler.toDomain(
                persistenceRepository.saveAndFlush(GeofencePolicyPersistenceAssembler.toPersistence(policy)));
    }

    @Override
    public Optional<GeofencePolicy> findLatestByDeliveryId(Long deliveryId) {
        return persistenceRepository.findFirstByDeliveryIdOrderByPolicyVersionDesc(deliveryId)
                .map(GeofencePolicyPersistenceAssembler::toDomain);
    }

    @Override
    public List<GeofencePolicy> findByDeliveryId(Long deliveryId) {
        return persistenceRepository.findByDeliveryIdOrderByPolicyVersionDesc(deliveryId).stream()
                .map(GeofencePolicyPersistenceAssembler::toDomain)
                .toList();
    }
}
