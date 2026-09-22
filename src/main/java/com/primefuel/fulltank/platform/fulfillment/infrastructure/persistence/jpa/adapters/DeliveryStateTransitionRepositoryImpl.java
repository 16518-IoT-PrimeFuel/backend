package com.primefuel.fulltank.platform.fulfillment.infrastructure.persistence.jpa.adapters;

import com.primefuel.fulltank.platform.fulfillment.domain.model.entities.DeliveryStateTransition;
import com.primefuel.fulltank.platform.fulfillment.domain.repositories.DeliveryStateTransitionRepository;
import com.primefuel.fulltank.platform.fulfillment.infrastructure.persistence.jpa.assemblers.DeliveryStateTransitionAssembler;
import com.primefuel.fulltank.platform.fulfillment.infrastructure.persistence.jpa.repositories.DeliveryStateTransitionPersistenceRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class DeliveryStateTransitionRepositoryImpl implements DeliveryStateTransitionRepository {

    private final DeliveryStateTransitionPersistenceRepository persistenceRepository;

    public DeliveryStateTransitionRepositoryImpl(DeliveryStateTransitionPersistenceRepository persistenceRepository) {
        this.persistenceRepository = persistenceRepository;
    }

    @Override
    public DeliveryStateTransition add(DeliveryStateTransition transition) {
        var entity = DeliveryStateTransitionAssembler.toPersistence(transition);
        return DeliveryStateTransitionAssembler.toDomain(persistenceRepository.save(entity));
    }

    @Override
    public List<DeliveryStateTransition> findByDeliveryId(Long deliveryId) {
        return persistenceRepository.findByDeliveryIdOrderByIdAsc(deliveryId).stream()
                .map(DeliveryStateTransitionAssembler::toDomain).toList();
    }
}
