package com.primefuel.fulltank.platform.notification.interfaces.rest;

import com.primefuel.fulltank.platform.notification.application.queryservices.NotificationQueryService;
import com.primefuel.fulltank.platform.notification.domain.model.queries.GetNotificationsByUserIdQuery;
import com.primefuel.fulltank.platform.notification.domain.model.queries.GetUnreadNotificationsByUserIdQuery;
import com.primefuel.fulltank.platform.notification.interfaces.rest.resources.NotificationResource;
import com.primefuel.fulltank.platform.notification.interfaces.rest.transform.NotificationResourceFromEntityAssembler;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping(value = "/api/v2/users/{userId}/notifications", produces = MediaType.APPLICATION_JSON_VALUE)
public class NotificationsV2Controller {
    private final NotificationQueryService notifications;

    public NotificationsV2Controller(NotificationQueryService notifications) {
        this.notifications = notifications;
    }

    @GetMapping
    @PreAuthorize("@currentUserAccess.ownsUser(#userId)")
    public ResponseEntity<List<NotificationResource>> getAll(@PathVariable Long userId) {
        var resources = notifications.handle(new GetNotificationsByUserIdQuery(userId)).stream()
                .map(NotificationResourceFromEntityAssembler::toResourceFromEntity).toList();
        return ResponseEntity.ok(resources);
    }

    @GetMapping("/unread")
    @PreAuthorize("@currentUserAccess.ownsUser(#userId)")
    public ResponseEntity<List<NotificationResource>> getUnread(@PathVariable Long userId) {
        var resources = notifications.handle(new GetUnreadNotificationsByUserIdQuery(userId)).stream()
                .map(NotificationResourceFromEntityAssembler::toResourceFromEntity).toList();
        return ResponseEntity.ok(resources);
    }
}
