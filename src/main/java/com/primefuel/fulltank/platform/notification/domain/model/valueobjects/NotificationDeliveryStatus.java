package com.primefuel.fulltank.platform.notification.domain.model.valueobjects;

/** Outcome of a fanout delivery attempt persisted on the notification row (S20/T20-A). */
public enum NotificationDeliveryStatus {
    DELIVERED,
    FAILED
}
