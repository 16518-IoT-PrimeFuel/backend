package com.primefuel.fulltank.platform.notification.application.internal.eventhandlers;

import com.primefuel.fulltank.platform.notification.application.commandservices.NotificationCommandService;
import com.primefuel.fulltank.platform.notification.domain.model.commands.CreateNotificationCommand;
import com.primefuel.fulltank.platform.notification.domain.model.valueobjects.NotificationType;
import com.primefuel.fulltank.platform.shared.application.events.DurableEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class DurableEventNotificationHandler {
    private final NotificationCommandService notifications;

    public DurableEventNotificationHandler(NotificationCommandService notifications) {
        this.notifications = notifications;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(DurableEvent event) {
        var userId = userId(event.payload());
        if (userId == null) return;
        var type = switch (event.eventType()) {
            case "FuelRequestCreated" -> NotificationType.NEW_REQUEST;
            case "FuelRequestApproved" -> NotificationType.ORDER_ACCEPTED;
            case "FuelRequestRejected" -> NotificationType.ORDER_REJECTED;
            default -> null;
        };
        if (type == null) return;
        notifications.handle(new CreateNotificationCommand(userId, type,
                title(type), "Event " + event.eventType() + " for " + event.aggregateType(),
                Long.valueOf(event.aggregateId()), event.eventKey()));
    }

    private static Long userId(String payload) {
        for (var part : payload.split(";")) {
            if (part.startsWith("userId=")) {
                var value = part.substring("userId=".length());
                return value.isBlank() ? null : Long.valueOf(value);
            }
        }
        return null;
    }

    private static String title(NotificationType type) {
        return switch (type) {
            case NEW_REQUEST -> "New fuel request";
            case ORDER_ACCEPTED -> "Fuel request approved";
            case ORDER_REJECTED -> "Fuel request rejected";
            default -> "PrimeFuel update";
        };
    }
}
