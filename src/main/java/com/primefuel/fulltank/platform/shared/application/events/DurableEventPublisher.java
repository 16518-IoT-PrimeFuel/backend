package com.primefuel.fulltank.platform.shared.application.events;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DurableEventPublisher {
    private final DurableEventStore store;

    public DurableEventPublisher(DurableEventStore store) {
        this.store = store;
    }

    @Transactional
    public boolean publish(DurableEvent event) {
        return store.saveIfAbsent(event);
    }
}
