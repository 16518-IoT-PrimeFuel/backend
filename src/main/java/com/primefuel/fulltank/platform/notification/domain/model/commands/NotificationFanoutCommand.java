package com.primefuel.fulltank.platform.notification.domain.model.commands;

import com.primefuel.fulltank.platform.notification.domain.model.valueobjects.NotificationChannel;
import com.primefuel.fulltank.platform.notification.domain.model.valueobjects.NotificationType;

/**
 * Creates one notification for one recipient from a domain event (S20/T20-A). The {@code eventId} plus the
 * recipient and channel form the fanout idempotency key: a replay of the same event can never create a
 * second row for the same recipient.
 */
public record NotificationFanoutCommand(
        Long userId,
        Long organizationId,
        String eventId,
        NotificationType type,
        String title,
        String message,
        Long referenceId,
        NotificationChannel channel) {
}
