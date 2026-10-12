package com.primefuel.fulltank.platform.iam.interfaces.rest;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import com.primefuel.fulltank.platform.iam.api.MembershipAccess;
import com.primefuel.fulltank.platform.iam.domain.model.aggregates.User;
import com.primefuel.fulltank.platform.iam.domain.model.valueobjects.Roles;
import com.primefuel.fulltank.platform.iam.domain.repositories.RoleRepository;
import com.primefuel.fulltank.platform.iam.domain.repositories.UserRepository;
import com.primefuel.fulltank.platform.iam.infrastructure.persistence.jpa.entities.AdminAuditLogEntity;
import com.primefuel.fulltank.platform.iam.infrastructure.persistence.jpa.repositories.AdminAuditLogRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/admin/users")
@Tag(name = "Administración de plataforma", description = "Operaciones administrativas globales de cuentas")
public class AdminUsersController {
    private final UserRepository users;
    private final RoleRepository roles;
    private final MembershipAccess membershipAccess;
    private final AdminAuditLogRepository auditLog;

    public AdminUsersController(UserRepository users, RoleRepository roles,
                                MembershipAccess membershipAccess, AdminAuditLogRepository auditLog) {
        this.users = users;
        this.roles = roles;
        this.membershipAccess = membershipAccess;
        this.auditLog = auditLog;
    }

    /** Retira ROLE_ADMIN y conserva los demás roles. No permite dejar la plataforma sin administradores. */
    @PostMapping("/{userId}/demote")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @Transactional
    @Operation(summary = "Retirar rol de administrador de plataforma",
            description = "Quita ROLE_ADMIN a la cuenta sin tocar los demás roles. No se puede retirar al último administrador.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "La cuenta ya no tiene el rol ROLE_ADMIN."),
            @ApiResponse(responseCode = "403", description = "El usuario no tiene autoridad de administrador de plataforma."),
            @ApiResponse(responseCode = "404", description = "La cuenta no existe."),
            @ApiResponse(responseCode = "409", description = "La cuenta es el último administrador de plataforma.")
    })
    public ResponseEntity<PromotedUser> demote(@PathVariable Long userId) {
        var user = users.findById(userId);
        if (user.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        var target = user.get();
        if (hasAdminRole(target) && users.findAll().stream().filter(this::hasAdminRole).count() <= 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No se puede retirar el rol al último administrador");
        }
        target.removeRole(Roles.ROLE_ADMIN);
        target.revokeIssuedTokens(); // a demoted admin must lose the sessions that still carry ROLE_ADMIN
        var demoted = users.save(target);
        audit("DEMOTE", userId);
        return ResponseEntity.ok(toResponse(demoted));
    }

    /** Otorga acceso de administración de plataforma y conserva los demás roles del usuario. */
    @PostMapping("/{userId}/promote")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @Transactional
    @Operation(summary = "Otorgar rol de administrador de plataforma",
            description = "Añade ROLE_ADMIN a la cuenta sin retirar los roles existentes. Solo puede hacerlo un administrador de plataforma.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "La cuenta tiene el rol ROLE_ADMIN."),
            @ApiResponse(responseCode = "403", description = "El usuario no tiene autoridad de administrador de plataforma."),
            @ApiResponse(responseCode = "404", description = "La cuenta no existe.")
    })
    public ResponseEntity<PromotedUser> promote(@PathVariable Long userId) {
        var user = users.findById(userId);
        if (user.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        var adminRole = roles.findByName(Roles.ROLE_ADMIN).orElseThrow();
        var promoted = user.get().addRole(adminRole);
        users.save(promoted);
        audit("PROMOTE", userId);
        return ResponseEntity.ok(toResponse(promoted));
    }

    private void audit(String action, Long targetUserId) {
        var actorId = membershipAccess.currentUserId()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN));
        auditLog.save(new AdminAuditLogEntity(actorId, action, targetUserId, Instant.now()));
    }

    private boolean hasAdminRole(User user) {
        return user.getRoles().stream().anyMatch(role -> role.getName() == Roles.ROLE_ADMIN);
    }

    private static PromotedUser toResponse(User user) {
        return new PromotedUser(user.getId(), user.getUsername(),
                user.getRoles().stream().map(role -> role.getName().name()).sorted().toList());
    }

    public record PromotedUser(Long userId, String username, List<String> roles) { }
}
