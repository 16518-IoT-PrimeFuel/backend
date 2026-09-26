package com.primefuel.fulltank.platform.notification;

import com.primefuel.fulltank.platform.notification.application.internal.commandservices.NotificationCommandServiceImpl;
import com.primefuel.fulltank.platform.notification.domain.model.aggregates.Notification;
import com.primefuel.fulltank.platform.notification.domain.model.commands.CreateNotificationCommand;
import com.primefuel.fulltank.platform.notification.domain.model.valueobjects.NotificationType;
import com.primefuel.fulltank.platform.notification.domain.repositories.NotificationRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class NotificationIdempotencyTest {
    @Test
    void sourceEventReplayReturnsTheExistingNotification() {
        var repository = mock(NotificationRepository.class);
        var existing = new Notification();
        when(repository.findBySourceEventKey("evt-1")).thenReturn(Optional.of(existing));
        var service = new NotificationCommandServiceImpl(repository);

        var result = service.handle(new CreateNotificationCommand(7L, NotificationType.NEW_REQUEST,
                "Request", "Created", 9L, "evt-1"));

        assertSame(existing, result.toOptional().orElseThrow());
        verify(repository, never()).save(any());
    }
}
