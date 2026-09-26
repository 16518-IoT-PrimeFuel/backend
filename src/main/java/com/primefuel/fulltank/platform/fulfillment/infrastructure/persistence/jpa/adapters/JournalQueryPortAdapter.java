package com.primefuel.fulltank.platform.fulfillment.infrastructure.persistence.jpa.adapters;

import com.primefuel.fulltank.platform.fulfillment.application.ports.JournalEntryData;
import com.primefuel.fulltank.platform.fulfillment.application.ports.JournalQueryPort;
import com.primefuel.fulltank.platform.fulfillment.infrastructure.persistence.jpa.repositories.DeliveryJournalJpaRepository;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class JournalQueryPortAdapter implements JournalQueryPort {
    private final DeliveryJournalJpaRepository repository;

    public JournalQueryPortAdapter(DeliveryJournalJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<JournalEntryData> findByDeliveryId(Long deliveryId) {
        return repository.findByDeliveryIdOrderByOccurredAtAsc(deliveryId).stream()
                .map(entry -> new JournalEntryData(entry.getEventId(), entry.getDeliveryId(), entry.getEventType(),
                        entry.getOccurredAt(), entry.getPayload()))
                .toList();
    }
}
