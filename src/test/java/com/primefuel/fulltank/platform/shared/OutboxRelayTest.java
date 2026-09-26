package com.primefuel.fulltank.platform.shared;

import com.primefuel.fulltank.platform.shared.infrastructure.events.OutboxRelay;
import com.primefuel.fulltank.platform.shared.infrastructure.persistence.jpa.entities.OutboxEventPersistenceEntity;
import com.primefuel.fulltank.platform.shared.infrastructure.persistence.jpa.repositories.OutboxEventJpaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OutboxRelayTest {
    @Test
    void publishesPendingEventsAndMarksThemPublished() {
        var repository = mock(OutboxEventJpaRepository.class);
        var publisher = mock(ApplicationEventPublisher.class);
        var relay = new OutboxRelay(repository, publisher);
        var entity = new OutboxEventPersistenceEntity();
        entity.setEventKey("evt-1");
        entity.setEventType("FuelRequestCreated");
        entity.setAggregateType("FuelRequest");
        entity.setAggregateId("9");
        entity.setPayload("userId=7");
        entity.setOccurredAt(Instant.parse("2026-09-25T10:00:00Z"));
        entity.setStatus("PENDING");
        entity.setAttempts(0);

        relay.relay(List.of(entity));

        verify(publisher).publishEvent(any(Object.class));
        verify(repository).save(entity);
        assertEquals("PUBLISHED", entity.getStatus());
        assertEquals(1, entity.getAttempts());
    }
}
