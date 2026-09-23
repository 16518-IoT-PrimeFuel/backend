package com.primefuel.fulltank.platform.safety.infrastructure.persistence.jpa.repositories;

import com.primefuel.fulltank.platform.safety.infrastructure.persistence.jpa.entities.GeofencePolicyPersistenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GeofencePolicyPersistenceRepository
        extends JpaRepository<GeofencePolicyPersistenceEntity, Long> {

    Optional<GeofencePolicyPersistenceEntity> findFirstByDeliveryIdOrderByPolicyVersionDesc(Long deliveryId);

    List<GeofencePolicyPersistenceEntity> findByDeliveryIdOrderByPolicyVersionDesc(Long deliveryId);
}
