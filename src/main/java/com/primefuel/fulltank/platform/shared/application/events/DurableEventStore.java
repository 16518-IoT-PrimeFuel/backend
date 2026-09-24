package com.primefuel.fulltank.platform.shared.application.events;

public interface DurableEventStore {
    boolean saveIfAbsent(DurableEvent event);
}
