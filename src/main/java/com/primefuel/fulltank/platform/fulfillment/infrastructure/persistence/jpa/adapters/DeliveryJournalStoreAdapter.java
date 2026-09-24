package com.primefuel.fulltank.platform.fulfillment.infrastructure.persistence.jpa.adapters;

import com.primefuel.fulltank.platform.fulfillment.application.ports.DeliveryJournalStore;
import com.primefuel.fulltank.platform.fulfillment.infrastructure.persistence.jpa.entities.DeliveryJournalPersistenceEntity;
import com.primefuel.fulltank.platform.fulfillment.infrastructure.persistence.jpa.repositories.DeliveryJournalJpaRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class DeliveryJournalStoreAdapter implements DeliveryJournalStore {
    private final DeliveryJournalJpaRepository repository;

    public DeliveryJournalStoreAdapter(DeliveryJournalJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean append(String eventId, Long deliveryId, String eventType, Instant occurredAt, String payload) {
        if (repository.existsByEventId(eventId)) return false;
        var entry = new DeliveryJournalPersistenceEntity();
        entry.setEventId(eventId);
        entry.setDeliveryId(deliveryId);
        entry.setEventType(eventType);
        entry.setOccurredAt(occurredAt);
        entry.setPayload(payload);
        repository.save(entry);
        return true;
    }
}
