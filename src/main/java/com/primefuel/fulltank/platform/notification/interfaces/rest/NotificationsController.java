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
@Tag(name = "Notificaciones", description = "Consulta de notificaciones y gestión de su estado de lectura")
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
     * Crea una notificación para un único destinatario.
     *
     * <p>Debe indicarse exactamente uno de userId, companyId o providerId y el usuario debe pertenecer al destinatario.
     * Las empresas se resuelven a su usuario asociado; si no existe, se responde con una solicitud inválida.</p>
     *
     * <p><strong>Obsoleto:</strong> la bandeja se genera a partir de eventos y se consulta en
     * {@code /api/v2/me/notifications}. Esta ruta se conserva mientras existan consumidores registrados.</p>
     */
    @Deprecated
    @Operation(summary = "Crear notificación",
            description = "Operación obsoleta; se recomienda la bandeja generada por eventos en /api/v2/me/notifications. Crea una notificación para un usuario, empresa compradora o distribuidor del usuario autenticado.",
            deprecated = true)
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Notificación creada."),
            @ApiResponse(responseCode = "400", description = "No se indicó exactamente un destinatario o el usuario asociado no existe."),
            @ApiResponse(responseCode = "403", description = "El usuario no pertenece a ninguno de los destinatarios indicados."),
            @ApiResponse(responseCode = "404", description = "El destinatario indicado no pertenece al usuario autenticado.")
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
     * Marca una notificación como leída.
     *
     * <p>Solo el usuario destinatario puede actualizarla; una notificación ajena o inexistente se informa como no encontrada.</p>
     */
    @Operation(summary = "Marcar notificación como leída",
            description = "Actualiza el estado de lectura de una notificación perteneciente al usuario autenticado.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Notificación marcada como leída."),
            @ApiResponse(responseCode = "404", description = "La notificación no existe o no pertenece al usuario autenticado.")
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
     * Consulta una notificación por identificador.
     *
     * <p>Solo el usuario destinatario puede consultarla; una notificación ajena o inexistente se informa como no encontrada.</p>
     */
    @Operation(summary = "Consultar notificación por identificador",
            description = "Devuelve la notificación indicada cuando pertenece al usuario autenticado.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Notificación devuelta."),
            @ApiResponse(responseCode = "404", description = "La notificación no existe o no pertenece al usuario autenticado.")
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
     * Lista las notificaciones de un usuario.
     *
     * <p>El usuario solo puede consultar sus propias notificaciones.</p>
     */
    @Operation(summary = "Listar notificaciones por usuario",
            description = "Devuelve las notificaciones del usuario indicado cuando coincide con el usuario autenticado.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Se devuelve la lista de notificaciones."),
            @ApiResponse(responseCode = "403", description = "El usuario autenticado no coincide con el usuario solicitado.")
    })
    @GetMapping("/user/{userId}")
    @PreAuthorize("@currentUserAccess.ownsUser(#userId)")
    public ResponseEntity<List<NotificationResource>> getNotificationsByUser(@PathVariable Long userId) {
        var notifications = notificationQueryService.handle(new GetNotificationsByUserIdQuery(userId));
        var resources = notifications.stream().map(NotificationResourceFromEntityAssembler::toResourceFromEntity).toList();
        return new ResponseEntity<>(resources, HttpStatus.OK);
    }

    /**
     * Lista las notificaciones destinadas a una empresa compradora.
     *
     * <p>Solo el tenant propietario puede consultarlas. La empresa se resuelve a su usuario y, si no tiene uno, se devuelve una lista vacía.</p>
     */
    @Operation(summary = "Listar notificaciones por empresa compradora",
            description = "Devuelve las notificaciones del usuario asociado a la empresa compradora cuando el tenant autenticado es propietario.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Se devuelve la lista, que puede estar vacía si la empresa no tiene usuario asociado."),
            @ApiResponse(responseCode = "403", description = "La empresa solicitada no pertenece al tenant autenticado.")
    })
    @GetMapping("/buyer/{companyId}")
    @PreAuthorize("@currentUserAccess.ownsCompany(#companyId)")
    public ResponseEntity<List<NotificationResource>> getNotificationsByBuyer(@PathVariable Long companyId) {
        return userRepository.findByCompanyId(companyId)
                .map(user -> getNotificationsByUser(user.getId()))
                .orElse(ResponseEntity.ok(List.of()));
    }

    /**
     * Lista las notificaciones destinadas a un distribuidor.
     *
     * <p>Solo el tenant propietario puede consultarlas. El distribuidor se resuelve a su usuario asociado; si no existe, se devuelve una lista vacía.</p>
     */
    @Operation(summary = "Listar notificaciones por distribuidor",
            description = "Devuelve las notificaciones del usuario asociado al distribuidor cuando el tenant autenticado es propietario.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Se devuelve la lista, que puede estar vacía si el distribuidor no tiene usuario asociado."),
            @ApiResponse(responseCode = "403", description = "El distribuidor solicitado no pertenece al tenant autenticado.")
    })
    @GetMapping("/provider/{providerId}")
    @PreAuthorize("@currentUserAccess.ownsProvider(#providerId)")
    public ResponseEntity<List<NotificationResource>> getNotificationsByProvider(@PathVariable Long providerId) {
        return userRepository.findByProviderId(providerId)
                .map(user -> getNotificationsByUser(user.getId()))
                .orElse(ResponseEntity.ok(List.of()));
    }

    /**
     * Lista las notificaciones no leídas de un usuario.
     *
     * <p>El usuario solo puede consultar sus propias notificaciones pendientes de lectura.</p>
     */
    @Operation(summary = "Listar notificaciones no leídas por usuario",
            description = "Devuelve las notificaciones no leídas cuando el identificador corresponde al usuario autenticado.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Se devuelve la lista de notificaciones no leídas."),
            @ApiResponse(responseCode = "403", description = "El usuario autenticado no coincide con el usuario solicitado.")
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
