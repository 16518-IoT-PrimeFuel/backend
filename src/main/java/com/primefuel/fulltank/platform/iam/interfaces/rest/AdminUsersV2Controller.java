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
@RequestMapping("/api/v2/admin/users")
@Tag(name = "Platform administration", description = "Platform-wide administrative operations")
public class AdminUsersV2Controller {
    private final UserRepository users;
    private final RoleRepository roles;

    public AdminUsersV2Controller(UserRepository users, RoleRepository roles) {
        this.users = users;
        this.roles = roles;
    }

    /** Grants platform administrator access while preserving every existing user role. */
    @PostMapping("/{userId}/promote")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @Transactional
    @Operation(summary = "Promote a user to platform administrator",
            description = "Adds ROLE_ADMIN to the user without removing existing roles.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User has ROLE_ADMIN."),
            @ApiResponse(responseCode = "403", description = "Caller is not a platform administrator."),
            @ApiResponse(responseCode = "404", description = "User does not exist.")
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
