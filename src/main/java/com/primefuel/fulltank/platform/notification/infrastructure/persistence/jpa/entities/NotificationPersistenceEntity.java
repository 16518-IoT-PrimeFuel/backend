package com.primefuel.fulltank.platform.notification.infrastructure.persistence.jpa.entities;

import com.primefuel.fulltank.platform.notification.domain.model.valueobjects.NotificationChannel;
import com.primefuel.fulltank.platform.notification.domain.model.valueobjects.NotificationDeliveryStatus;
import com.primefuel.fulltank.platform.notification.domain.model.valueobjects.NotificationType;
import com.primefuel.fulltank.platform.shared.infrastructure.persistence.jpa.entities.AuditableAbstractPersistenceEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "notifications",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_notifications_event_recipient_channel",
                columnNames = {"event_id", "user_id", "channel"}))
@Getter
@Setter
@NoArgsConstructor
public class NotificationPersistenceEntity extends AuditableAbstractPersistenceEntity {

    @Column(nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 40, columnDefinition = "VARCHAR(40)")
    private NotificationType type;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "is_read", nullable = false)
    private boolean read;

    private Long referenceId;

    private Long organizationId;

    @Column(name = "event_id", length = 64)
    private String eventId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20, columnDefinition = "VARCHAR(20)")
    private NotificationChannel channel;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "delivery_status", nullable = false, length = 20, columnDefinition = "VARCHAR(20)")
    private NotificationDeliveryStatus deliveryStatus;

    @Column(nullable = false)
    private int attempts;

    private LocalDateTime lastAttemptAt;
}
