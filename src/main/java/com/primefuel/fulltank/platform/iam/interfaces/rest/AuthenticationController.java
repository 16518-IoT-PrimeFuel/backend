package com.primefuel.fulltank.platform.iam.interfaces.rest;

import com.primefuel.fulltank.platform.iam.application.commandservices.UserCommandService;
import com.primefuel.fulltank.platform.iam.application.internal.commandservices.PasswordResetService;
import com.primefuel.fulltank.platform.iam.interfaces.rest.resources.PasswordResetConfirmResource;
import com.primefuel.fulltank.platform.iam.interfaces.rest.resources.PasswordResetRequestResource;
import com.primefuel.fulltank.platform.iam.interfaces.rest.resources.SignInResource;
import com.primefuel.fulltank.platform.iam.interfaces.rest.resources.SignUpResource;
import com.primefuel.fulltank.platform.iam.interfaces.rest.transform.AuthenticatedUserResourceFromEntityAssembler;
import com.primefuel.fulltank.platform.iam.interfaces.rest.transform.SignInCommandFromResourceAssembler;
import com.primefuel.fulltank.platform.iam.interfaces.rest.transform.SignUpCommandFromResourceAssembler;
import com.primefuel.fulltank.platform.iam.interfaces.rest.transform.UserResourceFromEntityAssembler;
import com.primefuel.fulltank.platform.shared.interfaces.rest.transform.ResponseEntityAssembler;
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

    public AuthenticationController(UserCommandService userCommandService, PasswordResetService passwordResetService) {
        this.userCommandService = userCommandService;
        this.passwordResetService = passwordResetService;
    }

    @PostMapping("/sign-up")
    public ResponseEntity<?> signUp(@Valid @RequestBody SignUpResource resource) {
        var command = SignUpCommandFromResourceAssembler.toCommandFromResource(resource);
        var result = userCommandService.handle(command);
        return ResponseEntityAssembler.toResponseEntityFromResult(
                result,
                UserResourceFromEntityAssembler::toResourceFromEntity,
                HttpStatus.CREATED);
    }

    @PostMapping("/sign-in")
    public ResponseEntity<?> signIn(@RequestBody SignInResource resource) {
        var command = SignInCommandFromResourceAssembler.toCommandFromResource(resource);
        var result = userCommandService.handle(command);
        return ResponseEntityAssembler.toResponseEntityFromResult(
                result,
                pair -> AuthenticatedUserResourceFromEntityAssembler.toResourceFromEntity(
                        pair.getLeft(), pair.getRight()),
                HttpStatus.OK);
    }

    @PostMapping("/password-reset/request")
    public ResponseEntity<?> requestPasswordReset(@Valid @RequestBody PasswordResetRequestResource resource) {
        passwordResetService.request(resource.email());
        return ResponseEntity.accepted().body(java.util.Map.of(
                "message", "If the account exists, password reset instructions have been sent."));
    }

    @PostMapping("/password-reset/confirm")
    public ResponseEntity<?> confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmResource resource) {
        passwordResetService.confirm(resource.token(), resource.newPassword());
        return ResponseEntity.noContent().build();
    }
}
