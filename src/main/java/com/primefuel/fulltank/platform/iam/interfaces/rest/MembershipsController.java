package com.primefuel.fulltank.platform.iam.interfaces.rest;

import com.primefuel.fulltank.platform.iam.application.internal.commandservices.TenantMembershipCommandService;
import com.primefuel.fulltank.platform.iam.interfaces.rest.resources.InviteMembershipResource;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping(value = "/api/v2/provider-companies/{providerId}/memberships",
        produces = MediaType.APPLICATION_JSON_VALUE)
public class MembershipsController {
    private final TenantMembershipCommandService service;

    public MembershipsController(TenantMembershipCommandService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("@currentUserAccess.ownsProvider(#providerId)")
    public ResponseEntity<Map<String, Object>> invite(@PathVariable Long providerId,
                                                        @Valid @RequestBody InviteMembershipResource resource) {
        try {
            var userId = service.invite(providerId, resource.username(), resource.role());
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of("userId", userId, "status", "ACTIVE"));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", exception.getMessage()));
        }
    }

    @DeleteMapping("/{userId}")
    @PreAuthorize("@currentUserAccess.ownsProvider(#providerId)")
    public ResponseEntity<Void> revoke(@PathVariable Long providerId, @PathVariable Long userId) {
        return service.revoke(providerId, userId)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }
}
