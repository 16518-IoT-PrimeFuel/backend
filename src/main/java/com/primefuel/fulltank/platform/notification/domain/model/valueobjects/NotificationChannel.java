package com.primefuel.fulltank.platform.notification.domain.model.valueobjects;

/**
 * Delivery channel of a notification (S20/T20-A). U17 resolved the initial scope to <strong>in-app only</strong>;
 * push/marketing channels are explicitly out of scope, so {@code IN_APP} is the only value for now.
 */
public enum NotificationChannel {
    IN_APP
}
