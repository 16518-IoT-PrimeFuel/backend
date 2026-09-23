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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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

    /**
     * Invites an email address to join an organization.
     *
     * <p>Only members of the target organization may invite. A pending invitation for the same email
     * cannot be duplicated; the role is resolved from the request and must be a known membership role.
     * The invitation carries a single-use token with a limited lifetime.</p>
     */
    @Operation(summary = "Invite a member to an organization",
            description = "Creates a pending membership invitation for the given email and role in the target organization.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Invitation created."),
            @ApiResponse(responseCode = "400", description = "Request body failed validation or the role is not a known membership role."),
            @ApiResponse(responseCode = "403", description = "Caller does not belong to the target organization."),
            @ApiResponse(responseCode = "404", description = "Organization does not exist."),
            @ApiResponse(responseCode = "409", description = "A pending invitation already exists for this email.")
    })
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

    /**
     * Accepts a pending invitation for the authenticated user.
     *
     * <p>The caller must be authenticated. The token must still be usable (not expired, accepted or
     * revoked); accepting grants the invited role as an active membership in the organization.</p>
     */
    @Operation(summary = "Accept an invitation",
            description = "Accepts the invitation identified by its token and grants the invited membership to the caller.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Invitation accepted and membership granted."),
            @ApiResponse(responseCode = "403", description = "Caller is not authenticated."),
            @ApiResponse(responseCode = "404", description = "No invitation matches the given token."),
            @ApiResponse(responseCode = "409", description = "A membership conflict occurred while granting access."),
            @ApiResponse(responseCode = "422", description = "The invitation is expired, already accepted or revoked.")
    })
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

    /**
     * Revokes a pending invitation.
     *
     * <p>Only members of the invitation's organization may revoke it, and only while it is pending.</p>
     */
    @Operation(summary = "Revoke an invitation",
            description = "Revokes a pending invitation owned by the caller's organization.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Invitation revoked."),
            @ApiResponse(responseCode = "403", description = "Caller does not belong to the invitation's organization."),
            @ApiResponse(responseCode = "404", description = "Invitation does not exist."),
            @ApiResponse(responseCode = "409", description = "The invitation is not pending and cannot be revoked.")
    })
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
