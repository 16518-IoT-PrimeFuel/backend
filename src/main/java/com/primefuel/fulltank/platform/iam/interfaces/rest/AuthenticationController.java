package com.primefuel.fulltank.platform.iam.interfaces.rest;

import com.primefuel.fulltank.platform.iam.application.commandservices.UserCommandService;
import com.primefuel.fulltank.platform.iam.application.internal.commandservices.PasswordResetService;
import com.primefuel.fulltank.platform.iam.application.queryservices.MembershipQueryService;
import com.primefuel.fulltank.platform.iam.domain.model.queries.GetMembershipsByUserIdQuery;
import com.primefuel.fulltank.platform.iam.interfaces.rest.resources.PasswordResetConfirmResource;
import com.primefuel.fulltank.platform.iam.interfaces.rest.resources.PasswordResetRequestResource;
import com.primefuel.fulltank.platform.iam.interfaces.rest.resources.SignInResource;
import com.primefuel.fulltank.platform.iam.interfaces.rest.resources.SignUpResource;
import com.primefuel.fulltank.platform.iam.interfaces.rest.transform.AuthenticatedUserResourceFromEntityAssembler;
import com.primefuel.fulltank.platform.iam.interfaces.rest.transform.SignInCommandFromResourceAssembler;
import com.primefuel.fulltank.platform.iam.interfaces.rest.transform.SignUpCommandFromResourceAssembler;
import com.primefuel.fulltank.platform.iam.interfaces.rest.transform.UserResourceFromEntityAssembler;
import com.primefuel.fulltank.platform.shared.interfaces.rest.transform.ResponseEntityAssembler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;

@RestController
@RequestMapping(value = "/api/v1/authentication", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Authentication", description = "Authentication endpoints")
public class AuthenticationController {

    private final UserCommandService userCommandService;
    private final PasswordResetService passwordResetService;
    private final MembershipQueryService membershipQueryService;

    public AuthenticationController(UserCommandService userCommandService, PasswordResetService passwordResetService,
                                    MembershipQueryService membershipQueryService) {
        this.userCommandService = userCommandService;
        this.passwordResetService = passwordResetService;
        this.membershipQueryService = membershipQueryService;
    }

    /**
     * Registers a new buyer or provider account and bootstraps its organization.
     *
     * <p>Public endpoint. Exactly one role must be supplied together with the matching business
     * profile (buyer or provider, never both); the username is tied to the buyer company's contact
     * email and the company RUC must be unique. On success the organization is created and the new
     * user is granted an OWNER membership within the same transaction.</p>
     */
    @Operation(summary = "Register a new account",
            description = "Creates a user account together with its organization and owner membership, "
                    + "validating that the selected role matches the business profile supplied.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Account and organization created."),
            @ApiResponse(responseCode = "400", description = "Request body failed validation or the role/profile combination is invalid."),
            @ApiResponse(responseCode = "404", description = "One of the referenced roles does not exist."),
            @ApiResponse(responseCode = "409", description = "The username, buyer RUC, provider RUC or organization RUC already exists."),
            @ApiResponse(responseCode = "500", description = "Unexpected error while bootstrapping the organization or membership.")
    })
    @PostMapping("/sign-up")
    public ResponseEntity<?> signUp(@Valid @RequestBody SignUpResource resource) {
        var command = SignUpCommandFromResourceAssembler.toCommandFromResource(resource);
        var result = userCommandService.handle(command);
        return ResponseEntityAssembler.toResponseEntityFromResult(
                result,
                UserResourceFromEntityAssembler::toResourceFromEntity,
                HttpStatus.CREATED);
    }

    /**
     * Authenticates an existing user and issues a bearer token.
     *
     * <p>Public endpoint. The supplied password is matched against the stored hash; on success the
     * response pairs the user resource with a freshly signed token and the caller's memberships.</p>
     */
    @Operation(summary = "Authenticate a user",
            description = "Verifies the supplied credentials and returns the authenticated user together with a JWT.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Credentials accepted; token issued."),
            @ApiResponse(responseCode = "400", description = "The username or password is incorrect."),
            @ApiResponse(responseCode = "404", description = "No user matches the given username.")
    })
    @PostMapping("/sign-in")
    public ResponseEntity<?> signIn(@RequestBody SignInResource resource) {
        var command = SignInCommandFromResourceAssembler.toCommandFromResource(resource);
        var result = userCommandService.handle(command);
        return ResponseEntityAssembler.toResponseEntityFromResult(
                result,
                signIn -> AuthenticatedUserResourceFromEntityAssembler.toResourceFromEntity(
                        signIn.user(), signIn.token(),
                        membershipQueryService.handle(new GetMembershipsByUserIdQuery(signIn.user().getId()))),
                HttpStatus.OK);
    }

    /**
     * Starts the password-reset flow for an email address.
     *
     * <p>Public endpoint. The response is intentionally identical whether or not the account exists,
     * so it never discloses registration. When the account exists, a single-use token valid for 30
     * minutes is emailed and any previous token is replaced.</p>
     */
    @Operation(summary = "Request a password reset",
            description = "Sends reset instructions to the address if an account exists, "
                    + "without revealing whether the account is registered.")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Reset request accepted."),
            @ApiResponse(responseCode = "400", description = "The email field failed validation.")
    })
    @PostMapping("/password-reset/request")
    public ResponseEntity<?> requestPasswordReset(@Valid @RequestBody PasswordResetRequestResource resource) {
        passwordResetService.request(resource.email());
        return ResponseEntity.accepted().body(java.util.Map.of(
                "message", "If the account exists, password reset instructions have been sent."));
    }

    /**
     * Completes a password reset using a valid token.
     *
     * <p>Public endpoint. The token must be unexpired and unused; on success it is consumed and the
     * password is replaced by a hash of the new value.</p>
     */
    @Operation(summary = "Confirm a password reset",
            description = "Applies the new password when the reset token is valid, single-use and not expired.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Password updated; no content returned."),
            @ApiResponse(responseCode = "400", description = "The token is missing, expired or already used, or the new password is invalid.")
    })
    @PostMapping("/password-reset/confirm")
    public ResponseEntity<?> confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmResource resource) {
        passwordResetService.confirm(resource.token(), resource.newPassword());
        return ResponseEntity.noContent().build();
    }
}
