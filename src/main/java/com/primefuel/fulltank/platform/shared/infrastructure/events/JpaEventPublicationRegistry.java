package com.primefuel.fulltank.platform.shared.infrastructure.events;

import com.primefuel.fulltank.platform.shared.events.EventEnvelope;
import com.primefuel.fulltank.platform.shared.events.EventPublicationRegistry;
import com.primefuel.fulltank.platform.shared.infrastructure.persistence.jpa.entities.EventPublicationPersistenceEntity;
import com.primefuel.fulltank.platform.shared.infrastructure.persistence.jpa.repositories.EventPublicationPersistenceRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class JpaEventPublicationRegistry implements EventPublicationRegistry {

    private final EventPublicationPersistenceRepository repository;
    private final ApplicationEventPublisher events;

    public JpaEventPublicationRegistry(EventPublicationPersistenceRepository repository,
                                       ApplicationEventPublisher events) {
        this.repository = repository;
        this.events = events;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public EventEnvelope publish(String eventType,
                                 String aggregateType,
                                 String aggregateId,
                                 Long organizationId,
                                 Long aggregateVersion,
                                 String payloadJson) {
        if (organizationId == null) {
            throw new IllegalArgumentException("organizationId is required to publish an event");
        }
        var envelope = new EventEnvelope(UUID.randomUUID(), eventType, aggregateType, aggregateId,
                organizationId, aggregateVersion, Instant.now(), payloadJson);
        repository.save(toEntity(envelope));
        // T20-A: besides the durable outbox row, the envelope is emitted in-process so listeners
        // (e.g. notification fanout) can consume contracts without a dispatcher. They run synchronously
        // inside the publisher's transaction; a listener must be defensive so it cannot break the producer.
        events.publishEvent(envelope);
        return envelope;
    }

    private EventPublicationPersistenceEntity toEntity(EventEnvelope envelope) {
        var entity = new EventPublicationPersistenceEntity();
        entity.setEventId(envelope.eventId().toString());
        entity.setEventType(envelope.eventType());
        entity.setAggregateType(envelope.aggregateType());
        entity.setAggregateId(envelope.aggregateId());
        entity.setOrganizationId(envelope.organizationId());
        entity.setAggregateVersion(envelope.aggregateVersion());
        entity.setOccurredAt(envelope.occurredAt());
        entity.setPayload(envelope.payload());
        return entity;
    }
}
