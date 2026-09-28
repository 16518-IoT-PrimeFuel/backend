package com.primefuel.fulltank.platform.notification.application.internal.listeners;

import com.primefuel.fulltank.platform.iam.api.MembershipDirectory;
import com.primefuel.fulltank.platform.notification.domain.model.aggregates.Notification;
import com.primefuel.fulltank.platform.notification.domain.model.commands.NotificationFanoutCommand;
import com.primefuel.fulltank.platform.notification.domain.model.valueobjects.NotificationChannel;
import com.primefuel.fulltank.platform.notification.domain.model.valueobjects.NotificationType;
import com.primefuel.fulltank.platform.notification.domain.repositories.NotificationRepository;
import com.primefuel.fulltank.platform.shared.events.EventEnvelope;
import com.primefuel.fulltank.platform.shared.events.EventInbox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * S20/T20-A fanout: turns a published event envelope into in-app notifications for the members of the
 * event's scope (organization). It is the "consumer" side of the contracts — it never reads operational
 * repositories of other modules.
 *
 * <p>Recipients come from {@link MembershipDirectory} (active members only), so a revoked membership stops
 * receiving new fanout. Idempotency is double-layered: the {@link EventInbox} consumes each event once for
 * this consumer (replay-safe), and the {@code (event_id, user_id, channel)} uniqueness makes a duplicate row
 * for the same recipient impossible.
 *
 * <p>It runs synchronously inside the producer's transaction, so it is deliberately defensive: a recipient
 * failure is logged and skipped rather than rolled back into the business operation.
 */
@Service
public class NotificationFanoutListener {

    public static final String CONSUMER = "notification-fanout";

    private static final Logger LOG = LoggerFactory.getLogger(NotificationFanoutListener.class);
    private static final NotificationChannel CHANNEL = NotificationChannel.IN_APP;

    private final EventInbox inbox;
    private final MembershipDirectory membershipDirectory;
    private final NotificationRepository notificationRepository;

    public NotificationFanoutListener(EventInbox inbox,
                                      MembershipDirectory membershipDirectory,
                                      NotificationRepository notificationRepository) {
        this.inbox = inbox;
        this.membershipDirectory = membershipDirectory;
        this.notificationRepository = notificationRepository;
    }

    @EventListener
    @Order(Ordered.LOWEST_PRECEDENCE)
    @Transactional
    public void on(EventEnvelope envelope) {
        var spec = NotificationFanoutSpec.forEventType(envelope.eventType());
        if (spec == null || envelope.organizationId() == null) {
            return;
        }
        // Replay-safe: the same event is consumed once for this consumer.
        if (!inbox.consume(CONSUMER, envelope.eventId().toString())) {
            return;
        }
        for (Long userId : membershipDirectory.activeMemberUserIds(envelope.organizationId())) {
            fanout(envelope, spec, userId);
        }
    }

    private void fanout(EventEnvelope envelope, NotificationFanoutSpec spec, Long userId) {
        var eventId = envelope.eventId().toString();
        if (notificationRepository.findByEventIdAndUserIdAndChannel(eventId, userId, CHANNEL).isPresent()) {
            return;
        }
        try {
            notificationRepository.save(new Notification(new NotificationFanoutCommand(
                    userId, envelope.organizationId(), eventId, spec.type(), spec.title(), spec.message(),
                    referenceId(envelope), CHANNEL)));
        } catch (RuntimeException exception) {
            LOG.warn("notification fanout failed eventId={} eventType={} userId={}: {}",
                    eventId, envelope.eventType(), userId, exception.getMessage());
        }
    }

    private static Long referenceId(EventEnvelope envelope) {
        if (envelope.aggregateId() == null) {
            return null;
        }
        try {
            return Long.valueOf(envelope.aggregateId());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    /** Event contract → notification. Only the events S20 cares about are mapped; others are ignored. */
    enum NotificationFanoutSpec {
        REQUEST_ACCEPTED("replenishment.accepted.v1", NotificationType.ORDER_ACCEPTED,
                "Solicitud aceptada", "Tu solicitud de reposición fue aceptada."),
        REQUEST_REJECTED("replenishment.rejected.v1", NotificationType.ORDER_REJECTED,
                "Solicitud rechazada", "Tu solicitud de reposición fue rechazada."),
        DELIVERY_COMPLETED("delivery.completed.v1", NotificationType.DELIVERY_COMPLETED,
                "Entrega completada", "La entrega de tu pedido fue completada."),
        DELIVERY_FAILED("delivery.failed.v1", NotificationType.DELIVERY_FAILED,
                "Entrega fallida", "La entrega de tu pedido no pudo completarse.");

        private final String eventType;
        private final NotificationType type;
        private final String title;
        private final String message;

        NotificationFanoutSpec(String eventType, NotificationType type, String title, String message) {
            this.eventType = eventType;
            this.type = type;
            this.title = title;
            this.message = message;
        }

        NotificationType type() {
            return type;
        }

        String title() {
            return title;
        }

        String message() {
            return message;
        }

        static NotificationFanoutSpec forEventType(String eventType) {
            if (eventType == null) {
                return null;
            }
            for (NotificationFanoutSpec spec : values()) {
                if (spec.eventType.equals(eventType)) {
                    return spec;
                }
            }
            return null;
        }
    }
}
