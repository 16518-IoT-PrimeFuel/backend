package com.primefuel.fulltank.platform.notification.interfaces.rest;

import com.primefuel.fulltank.platform.iam.api.MembershipAccess;
import com.primefuel.fulltank.platform.notification.application.commandservices.NotificationCommandService;
import com.primefuel.fulltank.platform.notification.application.queryservices.NotificationQueryService;
import com.primefuel.fulltank.platform.notification.domain.model.commands.MarkNotificationAsReadCommand;
import com.primefuel.fulltank.platform.notification.domain.model.queries.GetNotificationByIdQuery;
import com.primefuel.fulltank.platform.notification.domain.model.queries.GetNotificationsByUserIdQuery;
import com.primefuel.fulltank.platform.notification.domain.model.queries.GetUnreadNotificationsByUserIdQuery;
import com.primefuel.fulltank.platform.notification.interfaces.rest.resources.NotificationResource;
import com.primefuel.fulltank.platform.notification.interfaces.rest.transform.NotificationResourceFromEntityAssembler;
import com.primefuel.fulltank.platform.shared.interfaces.rest.transform.ResponseEntityAssembler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * S20/T20-B: the current user's own inbox. Every operation is scoped to the user resolved from the
 * principal — there is no user id in the path, so a caller can only ever read or mark their own
 * notifications. This is the v2 replacement for the v1 user/company/provider routes.
 */
@RestController
@RequestMapping(value = "/api/v2/me/notifications", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "My notifications", description = "Current user's in-app notification inbox (v2)")
public class MeNotificationsController {

    private final NotificationQueryService notificationQueryService;
    private final NotificationCommandService notificationCommandService;
    private final MembershipAccess membershipAccess;

    public MeNotificationsController(NotificationQueryService notificationQueryService,
                                     NotificationCommandService notificationCommandService,
                                     MembershipAccess membershipAccess) {
        this.notificationQueryService = notificationQueryService;
        this.notificationCommandService = notificationCommandService;
        this.membershipAccess = membershipAccess;
    }

    /** Lists the caller's notifications. */
    @Operation(summary = "List my notifications",
            description = "Returns the in-app notifications of the authenticated user.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Notifications returned."),
            @ApiResponse(responseCode = "403", description = "Caller is not authenticated.")
    })
    @GetMapping
    public ResponseEntity<List<NotificationResource>> list() {
        var userId = membershipAccess.currentUserId();
        if (userId.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }
        return ResponseEntity.ok(toResources(
                notificationQueryService.handle(new GetNotificationsByUserIdQuery(userId.get()))));
    }

    /** Lists the caller's unread notifications. */
    @Operation(summary = "List my unread notifications",
            description = "Returns the unread in-app notifications of the authenticated user.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Unread notifications returned."),
            @ApiResponse(responseCode = "403", description = "Caller is not authenticated.")
    })
    @GetMapping("/unread")
    public ResponseEntity<List<NotificationResource>> listUnread() {
        var userId = membershipAccess.currentUserId();
        if (userId.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }
        return ResponseEntity.ok(toResources(
                notificationQueryService.handle(new GetUnreadNotificationsByUserIdQuery(userId.get()))));
    }

    /**
     * Marks one of the caller's notifications as read.
     *
     * <p>A notification that does not belong to the caller is reported as not found. Repeating the
     * operation is idempotent: the state stays read and nothing is duplicated.</p>
     */
    @Operation(summary = "Mark one of my notifications as read",
            description = "Marks the notification as read when it belongs to the authenticated user; idempotent.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Notification marked as read."),
            @ApiResponse(responseCode = "403", description = "Caller is not authenticated."),
            @ApiResponse(responseCode = "404", description = "Notification does not exist or does not belong to the caller.")
    })
    @PostMapping("/{notificationId}/read")
    public ResponseEntity<?> markAsRead(@PathVariable Long notificationId) {
        var userId = membershipAccess.currentUserId();
        if (userId.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }
        var existing = notificationQueryService.handle(new GetNotificationByIdQuery(notificationId));
        if (existing.isEmpty() || !userId.get().equals(existing.get().getUserId())) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        var result = notificationCommandService.handle(new MarkNotificationAsReadCommand(notificationId));
        return ResponseEntityAssembler.toResponseEntityFromResult(
                result, NotificationResourceFromEntityAssembler::toResourceFromEntity, HttpStatus.OK);
    }

    private static List<NotificationResource> toResources(
            List<com.primefuel.fulltank.platform.notification.domain.model.aggregates.Notification> notifications) {
        return notifications.stream()
                .map(NotificationResourceFromEntityAssembler::toResourceFromEntity)
                .toList();
    }
}
