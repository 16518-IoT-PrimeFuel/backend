package com.primefuel.fulltank.platform.notification.interfaces.rest;

import com.primefuel.fulltank.platform.notification.application.commandservices.NotificationCommandService;
import com.primefuel.fulltank.platform.notification.application.queryservices.NotificationQueryService;
import com.primefuel.fulltank.platform.notification.domain.model.commands.MarkNotificationAsReadCommand;
import com.primefuel.fulltank.platform.notification.domain.model.queries.GetNotificationByIdQuery;
import com.primefuel.fulltank.platform.notification.domain.model.queries.GetNotificationsByUserIdQuery;
import com.primefuel.fulltank.platform.notification.domain.model.queries.GetUnreadNotificationsByUserIdQuery;
import com.primefuel.fulltank.platform.notification.interfaces.rest.resources.CreateNotificationResource;
import com.primefuel.fulltank.platform.notification.interfaces.rest.resources.NotificationResource;
import com.primefuel.fulltank.platform.notification.interfaces.rest.transform.CreateNotificationCommandFromResourceAssembler;
import com.primefuel.fulltank.platform.notification.interfaces.rest.transform.NotificationResourceFromEntityAssembler;
import com.primefuel.fulltank.platform.iam.domain.repositories.UserRepository;
import com.primefuel.fulltank.platform.iam.infrastructure.authorization.sfs.services.CurrentUserAccess;
import com.primefuel.fulltank.platform.shared.interfaces.rest.transform.ResponseEntityAssembler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;

@RestController
@RequestMapping(value = "/api/v1/notifications", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Notifications", description = "Notification management endpoints")
public class NotificationsController {

    private final NotificationCommandService notificationCommandService;
    private final NotificationQueryService notificationQueryService;
    private final UserRepository userRepository;
    private final CurrentUserAccess currentUserAccess;

    public NotificationsController(NotificationCommandService notificationCommandService,
                                   NotificationQueryService notificationQueryService,
                                   UserRepository userRepository,
                                   CurrentUserAccess currentUserAccess) {
        this.notificationCommandService = notificationCommandService;
        this.notificationQueryService = notificationQueryService;
        this.userRepository = userRepository;
        this.currentUserAccess = currentUserAccess;
    }

    /**
     * Creates a notification for exactly one recipient.
     *
     * <p>Exactly one of userId/companyId/providerId must be supplied, and the caller must own the
     * referenced recipient (user, company or provider). A company/provider recipient is resolved to its
     * user; when that user does not exist the request is rejected as a bad request.</p>
     *
     * <p><strong>Deprecated (S20/T20-B):</strong> the frontend must no longer fabricate notifications;
     * the inbox is generated from events (T20-A) and read through {@code /api/v2/me/notifications}. The
     * route is kept working (not removed) until the consumer ledger proves no caller (S22/T24-B).</p>
     */
    @Deprecated
    @Operation(summary = "Create a notification",
            description = "Deprecated: prefer the event-driven inbox and /api/v2/me/notifications. Creates a notification addressed to exactly one owned recipient (user, company or provider).",
            deprecated = true)
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Notification created."),
            @ApiResponse(responseCode = "400", description = "Not exactly one recipient was supplied, or the referenced recipient user does not exist."),
            @ApiResponse(responseCode = "403", description = "Caller owns none of the referenced user/company/provider."),
            @ApiResponse(responseCode = "404", description = "The referenced recipient is not owned by the caller.")
    })
    @PostMapping
    @PreAuthorize("@currentUserAccess.ownsUser(#resource.userId()) or @currentUserAccess.ownsCompany(#resource.companyId()) or @currentUserAccess.ownsProvider(#resource.providerId())")
    public ResponseEntity<?> createNotification(@RequestBody CreateNotificationResource resource) {
        int recipients = (resource.userId() == null ? 0 : 1)
                + (resource.companyId() == null ? 0 : 1)
                + (resource.providerId() == null ? 0 : 1);
        if (recipients != 1) return ResponseEntity.badRequest().body("Specify exactly one notification recipient");
        if (resource.userId() != null && !currentUserAccess.ownsUser(resource.userId())
                || resource.companyId() != null && !currentUserAccess.ownsCompany(resource.companyId())
                || resource.providerId() != null && !currentUserAccess.ownsProvider(resource.providerId())) {
            return ResponseEntity.notFound().build();
        }
        var userId = resolveUserId(resource);
        if (userId == null) {
            return ResponseEntity.badRequest().body("Notification recipient does not exist");
        }
        var resolved = new CreateNotificationResource(userId, resource.companyId(), resource.providerId(),
                resource.type(), resource.title(), resource.message(), resource.referenceId());
        var command = CreateNotificationCommandFromResourceAssembler.toCommandFromResource(resolved);
        var result = notificationCommandService.handle(command);
        return ResponseEntityAssembler.toResponseEntityFromResult(
                result,
                NotificationResourceFromEntityAssembler::toResourceFromEntity,
                HttpStatus.CREATED);
    }

    /**
     * Marks a notification as read.
     *
     * <p>Only the user the notification belongs to may change it; anything else is reported as not
     * found.</p>
     */
    @Operation(summary = "Mark a notification as read",
            description = "Marks the given notification as read when it belongs to the caller.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Notification marked as read."),
            @ApiResponse(responseCode = "404", description = "Notification does not exist or does not belong to the caller.")
    })
    @PostMapping("/{notificationId}/mark-as-read")
    public ResponseEntity<?> markAsRead(@PathVariable Long notificationId) {
        var notification = notificationQueryService.handle(new GetNotificationByIdQuery(notificationId));
        if (notification.isEmpty() || !currentUserAccess.ownsUser(notification.get().getUserId())) {
            return ResponseEntity.notFound().build();
        }
        var result = notificationCommandService.handle(new MarkNotificationAsReadCommand(notificationId));
        return ResponseEntityAssembler.toResponseEntityFromResult(
                result,
                NotificationResourceFromEntityAssembler::toResourceFromEntity,
                HttpStatus.OK);
    }

    /**
     * Retrieves a single notification.
     *
     * <p>Only the user it belongs to may read it; anything else is reported as not found.</p>
     */
    @Operation(summary = "Get a notification by id",
            description = "Returns the notification identified by the path id when it belongs to the caller.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Notification returned."),
            @ApiResponse(responseCode = "404", description = "Notification does not exist or does not belong to the caller.")
    })
    @GetMapping("/{notificationId}")
    public ResponseEntity<NotificationResource> getNotificationById(@PathVariable Long notificationId) {
        var result = notificationQueryService.handle(new GetNotificationByIdQuery(notificationId))
                .filter(notification -> currentUserAccess.ownsUser(notification.getUserId()));
        return result.map(n -> new ResponseEntity<>(
                        NotificationResourceFromEntityAssembler.toResourceFromEntity(n), HttpStatus.OK))
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    /**
     * Lists the notifications of a user.
     *
     * <p>A caller may only list their own notifications.</p>
     */
    @Operation(summary = "List notifications by user",
            description = "Returns the notifications of the given user when it is the caller.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Notifications returned."),
            @ApiResponse(responseCode = "403", description = "Caller is not the requested user.")
    })
    @GetMapping("/user/{userId}")
    @PreAuthorize("@currentUserAccess.ownsUser(#userId)")
    public ResponseEntity<List<NotificationResource>> getNotificationsByUser(@PathVariable Long userId) {
        var notifications = notificationQueryService.handle(new GetNotificationsByUserIdQuery(userId));
        var resources = notifications.stream().map(NotificationResourceFromEntityAssembler::toResourceFromEntity).toList();
        return new ResponseEntity<>(resources, HttpStatus.OK);
    }

    /**
     * Lists the notifications addressed to a buyer company.
     *
     * <p>Only the owning company may list them; the company is resolved to its user. A company without a
     * user yields an empty list rather than an error.</p>
     */
    @Operation(summary = "List notifications by buyer company",
            description = "Returns the notifications addressed to the given buyer company's user when the caller owns the company.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Notifications returned (possibly empty)."),
            @ApiResponse(responseCode = "403", description = "Caller does not own the requested company.")
    })
    @GetMapping("/buyer/{companyId}")
    @PreAuthorize("@currentUserAccess.ownsCompany(#companyId)")
    public ResponseEntity<List<NotificationResource>> getNotificationsByBuyer(@PathVariable Long companyId) {
        return userRepository.findByCompanyId(companyId)
                .map(user -> getNotificationsByUser(user.getId()))
                .orElse(ResponseEntity.ok(List.of()));
    }

    /**
     * Lists the notifications addressed to a provider tenant.
     *
     * <p>Only the owning provider may list them; the provider is resolved to its user. A provider without a
     * user yields an empty list rather than an error.</p>
     */
    @Operation(summary = "List notifications by provider",
            description = "Returns the notifications addressed to the given provider tenant's user when the caller owns the provider.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Notifications returned (possibly empty)."),
            @ApiResponse(responseCode = "403", description = "Caller does not own the requested provider tenant.")
    })
    @GetMapping("/provider/{providerId}")
    @PreAuthorize("@currentUserAccess.ownsProvider(#providerId)")
    public ResponseEntity<List<NotificationResource>> getNotificationsByProvider(@PathVariable Long providerId) {
        return userRepository.findByProviderId(providerId)
                .map(user -> getNotificationsByUser(user.getId()))
                .orElse(ResponseEntity.ok(List.of()));
    }

    /**
     * Lists the unread notifications of a user.
     *
     * <p>A caller may only list their own unread notifications.</p>
     */
    @Operation(summary = "List unread notifications by user",
            description = "Returns the unread notifications of the given user when it is the caller.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Unread notifications returned."),
            @ApiResponse(responseCode = "403", description = "Caller is not the requested user.")
    })
    @GetMapping("/user/{userId}/unread")
    @PreAuthorize("@currentUserAccess.ownsUser(#userId)")
    public ResponseEntity<List<NotificationResource>> getUnreadByUser(@PathVariable Long userId) {
        var notifications = notificationQueryService.handle(new GetUnreadNotificationsByUserIdQuery(userId));
        var resources = notifications.stream().map(NotificationResourceFromEntityAssembler::toResourceFromEntity).toList();
        return new ResponseEntity<>(resources, HttpStatus.OK);
    }

    private Long resolveUserId(CreateNotificationResource resource) {
        if (resource.userId() != null) return resource.userId();
        if (resource.companyId() != null) {
            return userRepository.findByCompanyId(resource.companyId()).map(user -> user.getId()).orElse(null);
        }
        if (resource.providerId() != null) {
            return userRepository.findByProviderId(resource.providerId()).map(user -> user.getId()).orElse(null);
        }
        return null;
    }
}
