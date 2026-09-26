package com.primefuel.fulltank.platform.shared.infrastructure.events;

import com.primefuel.fulltank.platform.shared.application.events.DurableEvent;
import com.primefuel.fulltank.platform.shared.infrastructure.persistence.jpa.entities.OutboxEventPersistenceEntity;
import com.primefuel.fulltank.platform.shared.infrastructure.persistence.jpa.repositories.OutboxEventJpaRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class OutboxRelay {
    private final OutboxEventJpaRepository repository;
    private final ApplicationEventPublisher events;

    public OutboxRelay(OutboxEventJpaRepository repository, ApplicationEventPublisher events) {
        this.repository = repository;
        this.events = events;
    }

    @Scheduled(fixedDelayString = "${app.outbox.relay-delay-ms:5000}")
    @Transactional
    public void relayPending() {
        relay(repository.findTop50ByStatusOrderByOccurredAtAsc("PENDING"));
    }

    public void relay(List<OutboxEventPersistenceEntity> pending) {
        for (var entity : pending) {
            events.publishEvent(new DurableEvent(entity.getEventKey(), entity.getEventType(),
                    entity.getAggregateType(), entity.getAggregateId(), entity.getPayload(),
                    entity.getOccurredAt()));
            entity.setAttempts(entity.getAttempts() + 1);
            entity.setStatus("PUBLISHED");
            repository.save(entity);
        }
    }
}
