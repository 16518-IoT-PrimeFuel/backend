package com.primefuel.fulltank.platform.shared.events;

public interface EventInbox {

    boolean consume(String consumer, String eventId);
}
