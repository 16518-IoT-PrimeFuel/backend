package com.primefuel.fulltank.platform.ordering.infrastructure.persistence.jpa.adapters;

import com.primefuel.fulltank.platform.ordering.application.ports.ReplenishmentLifecycleStore;
import com.primefuel.fulltank.platform.ordering.infrastructure.persistence.jpa.entities.ReplenishmentLifecyclePersistenceEntity;
import com.primefuel.fulltank.platform.ordering.infrastructure.persistence.jpa.repositories.ReplenishmentLifecycleJpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component
public class ReplenishmentLifecycleStoreAdapter implements ReplenishmentLifecycleStore {
    private final ReplenishmentLifecycleJpaRepository repository;

    public ReplenishmentLifecycleStoreAdapter(ReplenishmentLifecycleJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public java.util.Optional<Long> findRequestIdByIdempotencyKey(String key) {
        return repository.findByIdempotencyKey(key).map(ReplenishmentLifecyclePersistenceEntity::getRequestId);
    }

    @Override
    public void create(Long requestId, String key) {
        var entity = new ReplenishmentLifecyclePersistenceEntity();
        entity.setRequestId(requestId); entity.setIdempotencyKey(key); entity.setState("PENDING"); entity.setVersion(0);
        repository.save(entity);
    }

    @Override
    @Transactional
    public boolean transition(Long requestId, String expectedState, String nextState) {
        return repository.transition(requestId, expectedState, nextState) == 1;
    }

    @Override
    @Transactional
    public boolean consume(Long requestId, Long orderId) {
        return repository.consume(requestId, orderId, Instant.now()) == 1;
    }
}
