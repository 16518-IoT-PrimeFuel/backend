package com.primefuel.fulltank.platform.notification.domain.repositories;

import com.primefuel.fulltank.platform.notification.domain.model.aggregates.Notification;
import com.primefuel.fulltank.platform.notification.domain.model.valueobjects.NotificationChannel;

import java.util.List;
import java.util.Optional;

public interface NotificationRepository {
    Optional<Notification> findById(Long id);
    List<Notification> findByUserId(Long userId);
    List<Notification> findByUserIdAndReadFalse(Long userId);

    /** Fanout idempotency lookup: at most one notification per event + recipient + channel. */
    Optional<Notification> findByEventIdAndUserIdAndChannel(
            String eventId, Long userId, NotificationChannel channel);

    Notification save(Notification notification);
}
