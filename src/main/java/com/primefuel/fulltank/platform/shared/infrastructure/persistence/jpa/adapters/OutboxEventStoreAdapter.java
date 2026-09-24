package com.primefuel.fulltank.platform.shared.infrastructure.persistence.jpa.adapters;

import com.primefuel.fulltank.platform.shared.application.events.DurableEvent;
import com.primefuel.fulltank.platform.shared.application.events.DurableEventStore;
import com.primefuel.fulltank.platform.shared.infrastructure.persistence.jpa.entities.OutboxEventPersistenceEntity;
import com.primefuel.fulltank.platform.shared.infrastructure.persistence.jpa.repositories.OutboxEventJpaRepository;
import org.springframework.stereotype.Component;

@Component
public class OutboxEventStoreAdapter implements DurableEventStore {
    private final OutboxEventJpaRepository repository;

    public OutboxEventStoreAdapter(OutboxEventJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean saveIfAbsent(DurableEvent event) {
        if (repository.existsByEventKey(event.eventKey())) return false;
        var entity = new OutboxEventPersistenceEntity();
        entity.setEventKey(event.eventKey());
        entity.setEventType(event.eventType());
        entity.setAggregateType(event.aggregateType());
        entity.setAggregateId(event.aggregateId());
        entity.setPayload(event.payload());
        entity.setOccurredAt(event.occurredAt());
        entity.setStatus("PENDING");
        entity.setAttempts(0);
        repository.save(entity);
        return true;
    }
}
