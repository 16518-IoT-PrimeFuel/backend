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
 * Bandeja v2 del usuario autenticado. Todas las operaciones se limitan al usuario del principal; las rutas no
 * reciben un identificador de usuario y solo permiten consultar o actualizar sus propias notificaciones.
 */
@RestController
@RequestMapping(value = "/api/v2/me/notifications", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Mis notificaciones", description = "Bandeja personal de notificaciones de la API v2")
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

    /** Lista las notificaciones del usuario autenticado. */
    @Operation(summary = "Listar mis notificaciones",
            description = "Devuelve las notificaciones de la bandeja del usuario autenticado.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Se devuelve la lista de notificaciones."),
            @ApiResponse(responseCode = "403", description = "El usuario no está autenticado.")
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

    /** Lista las notificaciones no leídas del usuario autenticado. */
    @Operation(summary = "Listar mis notificaciones no leídas",
            description = "Devuelve las notificaciones pendientes de lectura en la bandeja del usuario autenticado.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Se devuelve la lista de notificaciones no leídas."),
            @ApiResponse(responseCode = "403", description = "El usuario no está autenticado.")
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
     * Marca como leída una notificación del usuario autenticado.
     *
     * <p>Una notificación ajena se informa como no encontrada. La operación es idempotente y no duplica efectos.</p>
     */
    @Operation(summary = "Marcar una notificación propia como leída",
            description = "Marca la notificación cuando pertenece al usuario autenticado; repetir la operación no duplica efectos.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Notificación marcada como leída."),
            @ApiResponse(responseCode = "403", description = "El usuario no está autenticado."),
            @ApiResponse(responseCode = "404", description = "La notificación no existe o no pertenece al usuario autenticado.")
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
