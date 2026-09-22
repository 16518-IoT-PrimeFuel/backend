package com.primefuel.fulltank.platform.iam.interfaces.rest;

import com.primefuel.fulltank.platform.iam.api.MembershipAccess;
import com.primefuel.fulltank.platform.iam.application.commandservices.InvitationCommandService;
import com.primefuel.fulltank.platform.iam.application.queryservices.InvitationQueryService;
import com.primefuel.fulltank.platform.iam.domain.model.commands.AcceptInvitationCommand;
import com.primefuel.fulltank.platform.iam.domain.model.commands.InviteMemberCommand;
import com.primefuel.fulltank.platform.iam.domain.model.commands.RevokeInvitationCommand;
import com.primefuel.fulltank.platform.iam.domain.model.queries.GetInvitationByIdQuery;
import com.primefuel.fulltank.platform.iam.domain.model.valueobjects.MembershipRole;
import com.primefuel.fulltank.platform.iam.interfaces.rest.resources.InviteMemberResource;
import com.primefuel.fulltank.platform.iam.interfaces.rest.transform.InvitationResourceFromDomainAssembler;
import com.primefuel.fulltank.platform.shared.application.result.ApplicationError;
import com.primefuel.fulltank.platform.shared.interfaces.rest.transform.ResponseEntityAssembler;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value = "/api/v2", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Invitations", description = "Organization membership invitations (v2)")
public class InvitationsController {

    private final InvitationCommandService invitationCommandService;
    private final InvitationQueryService invitationQueryService;
    private final MembershipAccess membershipAccess;

    public InvitationsController(InvitationCommandService invitationCommandService,
                                 InvitationQueryService invitationQueryService,
                                 MembershipAccess membershipAccess) {
        this.invitationCommandService = invitationCommandService;
        this.invitationQueryService = invitationQueryService;
        this.membershipAccess = membershipAccess;
    }

    @PostMapping("/organizations/{organizationId}/invitations")
    @PreAuthorize("@membershipAccess.belongsToOrganization(#organizationId)")
    public ResponseEntity<?> invite(@PathVariable Long organizationId,
                                    @Valid @RequestBody InviteMemberResource resource) {
        MembershipRole role;
        try {
            role = MembershipRole.valueOf(resource.role().trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest()
                    .body(ApplicationError.validationError("role", "Unknown membership role"));
        }
        var result = invitationCommandService.handle(new InviteMemberCommand(
                organizationId, resource.email(), role, membershipAccess.currentUserId().orElse(null)));
        return ResponseEntityAssembler.toResponseEntityFromResult(
                result, InvitationResourceFromDomainAssembler::toResourceFromDomain, HttpStatus.CREATED);
    }

    @PostMapping("/invitations/{token}/accept")
    public ResponseEntity<?> accept(@PathVariable String token) {
        var userId = membershipAccess.currentUserId();
        if (userId.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }
        var result = invitationCommandService.handle(new AcceptInvitationCommand(token, userId.get()));
        return ResponseEntityAssembler.toResponseEntityFromResult(
                result, InvitationResourceFromDomainAssembler::toResourceFromDomain, HttpStatus.OK);
    }

    @DeleteMapping("/invitations/{invitationId}")
    public ResponseEntity<?> revoke(@PathVariable Long invitationId) {
        var existing = invitationQueryService.handle(new GetInvitationByIdQuery(invitationId));
        if (existing.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        if (!membershipAccess.belongsToOrganization(existing.get().getOrganizationId())) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }
        var result = invitationCommandService.handle(new RevokeInvitationCommand(invitationId));
        return ResponseEntityAssembler.toResponseEntityFromResult(
                result, InvitationResourceFromDomainAssembler::toResourceFromDomain, HttpStatus.OK);
    }
}
