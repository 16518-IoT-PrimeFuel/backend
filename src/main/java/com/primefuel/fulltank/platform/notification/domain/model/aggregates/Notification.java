package com.primefuel.fulltank.platform.notification.domain.model.aggregates;

import com.primefuel.fulltank.platform.notification.domain.model.commands.CreateNotificationCommand;
import com.primefuel.fulltank.platform.notification.domain.model.commands.NotificationFanoutCommand;
import com.primefuel.fulltank.platform.notification.domain.model.valueobjects.NotificationChannel;
import com.primefuel.fulltank.platform.notification.domain.model.valueobjects.NotificationDeliveryStatus;
import com.primefuel.fulltank.platform.notification.domain.model.valueobjects.NotificationType;
import com.primefuel.fulltank.platform.shared.domain.model.aggregates.AbstractDomainAggregateRoot;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Date;

@Getter
@Setter
@NoArgsConstructor
public class Notification extends AbstractDomainAggregateRoot<Notification> {

    private Long id;
    private Long userId;
    private NotificationType type;
    private String title;
    private String message;
    private boolean read;
    private Long referenceId;
    private Date createdAt;

    private Long organizationId;
    private String eventId;
    private NotificationChannel channel;
    private NotificationDeliveryStatus deliveryStatus;
    private int attempts;
    private LocalDateTime lastAttemptAt;

    /** Manual creation (legacy v1 POST). Not event-driven, so it carries no fanout identity. */
    public Notification(CreateNotificationCommand command) {
        this.userId = command.userId();
        this.type = command.type();
        this.title = command.title();
        this.message = command.message();
        this.referenceId = command.referenceId();
        this.read = false;
        this.channel = NotificationChannel.IN_APP;
        this.deliveryStatus = NotificationDeliveryStatus.DELIVERED;
        this.attempts = 1;
        this.lastAttemptAt = LocalDateTime.now();
    }

    /** Event-driven fanout (S20/T20-A): idempotent by event + recipient + channel. */
    public Notification(NotificationFanoutCommand command) {
        this.userId = command.userId();
        this.organizationId = command.organizationId();
        this.eventId = command.eventId();
        this.type = command.type();
        this.title = command.title();
        this.message = command.message();
        this.referenceId = command.referenceId();
        this.channel = command.channel();
        this.read = false;
        this.deliveryStatus = NotificationDeliveryStatus.DELIVERED;
        this.attempts = 1;
        this.lastAttemptAt = LocalDateTime.now();
    }

    public void markAsRead() {
        this.read = true;
    }

    /** Records a failed delivery attempt (keeps the row so the attempt is observable). */
    public void recordFailedAttempt() {
        this.deliveryStatus = NotificationDeliveryStatus.FAILED;
        this.attempts = this.attempts + 1;
        this.lastAttemptAt = LocalDateTime.now();
    }
}
