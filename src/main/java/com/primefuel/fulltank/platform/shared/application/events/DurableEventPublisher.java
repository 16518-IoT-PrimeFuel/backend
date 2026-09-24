package com.primefuel.fulltank.platform.shared.application.events;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;

@Service
public class DurableEventPublisher {
    private final DurableEventStore store;
    private final ApplicationEventPublisher applicationEvents;

    public DurableEventPublisher(DurableEventStore store, ApplicationEventPublisher applicationEvents) {
        this.store = store;
        this.applicationEvents = applicationEvents;
    }

    @Transactional
    public boolean publish(DurableEvent event) {
        var saved = store.saveIfAbsent(event);
        if (saved) applicationEvents.publishEvent(event);
        return saved;
    }
}
