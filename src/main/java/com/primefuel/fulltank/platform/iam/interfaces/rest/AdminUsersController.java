package com.primefuel.fulltank.platform.iam.interfaces.rest;

import com.primefuel.fulltank.platform.iam.domain.model.valueobjects.Roles;
import com.primefuel.fulltank.platform.iam.domain.repositories.RoleRepository;
import com.primefuel.fulltank.platform.iam.domain.repositories.UserRepository;
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

import java.util.List;

@RestController
@RequestMapping("/api/admin/users")
@Tag(name = "Administración de plataforma", description = "Operaciones administrativas globales de cuentas")
public class AdminUsersController {
    private final UserRepository users;
    private final RoleRepository roles;

    public AdminUsersController(UserRepository users, RoleRepository roles) {
        this.users = users;
        this.roles = roles;
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
        if (user.isEmpty()) return ResponseEntity.notFound().build();
        var adminRole = roles.findByName(Roles.ROLE_ADMIN).orElseThrow();
        var promoted = user.get().addRole(adminRole);
        users.save(promoted);
        return ResponseEntity.ok(new PromotedUser(promoted.getId(), promoted.getUsername(),
                promoted.getRoles().stream().map(role -> role.getName().name()).sorted().toList()));
    }

    public record PromotedUser(Long userId, String username, List<String> roles) { }
}
