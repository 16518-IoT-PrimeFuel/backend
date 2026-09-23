package com.primefuel.fulltank.platform.iam.interfaces.rest;

import com.primefuel.fulltank.platform.iam.api.MembershipAccess;
import com.primefuel.fulltank.platform.iam.application.commandservices.OnboardingCommandService;
import com.primefuel.fulltank.platform.iam.domain.model.commands.OnboardOrganizationCommand;
import com.primefuel.fulltank.platform.iam.domain.model.valueobjects.MembershipRole;
import com.primefuel.fulltank.platform.iam.domain.model.valueobjects.OrganizationType;
import com.primefuel.fulltank.platform.iam.interfaces.rest.resources.OnboardOrganizationResource;
import com.primefuel.fulltank.platform.iam.interfaces.rest.transform.OrganizationResourceFromDomainAssembler;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/v2/onboarding", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Onboarding", description = "Organization onboarding endpoints (v2)")
public class OnboardingController {

    private final OnboardingCommandService onboardingCommandService;
    private final MembershipAccess membershipAccess;

    public OnboardingController(OnboardingCommandService onboardingCommandService,
                                MembershipAccess membershipAccess) {
        this.onboardingCommandService = onboardingCommandService;
        this.membershipAccess = membershipAccess;
    }

    /**
     * Creates a new organization owned by the authenticated user.
     *
     * <p>The caller must be authenticated; the owner is taken from the principal, never from the
     * request body. The type must be a known organization type, the RUC must be unique, and the
     * creator is granted an OWNER membership on the new organization.</p>
     */
    @Operation(summary = "Onboard an organization",
            description = "Creates an organization owned by the authenticated caller and grants the caller the OWNER membership.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Organization created and ownership granted."),
            @ApiResponse(responseCode = "400", description = "Request body failed validation, the type is unknown, or no owner could be resolved."),
            @ApiResponse(responseCode = "403", description = "Caller is not authenticated."),
            @ApiResponse(responseCode = "409", description = "An organization with the same RUC already exists, or ownership could not be granted.")
    })
    @PostMapping
    public ResponseEntity<?> onboard(@Valid @RequestBody OnboardOrganizationResource resource) {
        var ownerUserId = membershipAccess.currentUserId();
        if (ownerUserId.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }
        OrganizationType type;
        try {
            type = OrganizationType.valueOf(resource.type().trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest()
                    .body(ApplicationError.validationError("type", "Unknown organization type"));
        }
        var result = onboardingCommandService.handle(
                new OnboardOrganizationCommand(resource.name(), resource.ruc(), type, ownerUserId.get()));
        return ResponseEntityAssembler.toResponseEntityFromResult(
                result,
                organization -> OrganizationResourceFromDomainAssembler.toResourceFromDomain(
                        organization, MembershipRole.OWNER),
                HttpStatus.CREATED);
    }
}
