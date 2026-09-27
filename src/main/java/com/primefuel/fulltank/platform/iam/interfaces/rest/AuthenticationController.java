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
@RequestMapping(value = "/api/authentication", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Autenticación", description = "Registro, inicio de sesión y recuperación de contraseña")
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
     * Registra una cuenta de comprador o distribuidor y configura su organización.
     *
     * <p>Public endpoint. Exactly one role must be supplied together with the matching business
     * profile (buyer or provider, never both); the username is tied to the buyer company's contact
     * email and the company RUC must be unique. On success the organization is created and the new
     * user is granted an OWNER membership within the same transaction.</p>
     */
    @Operation(summary = "Registrar una cuenta",
            description = "Crea la cuenta y su organización, y asigna al usuario la membresía OWNER. El rol debe coincidir con el perfil comercial enviado.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Cuenta y organización creadas."),
            @ApiResponse(responseCode = "400", description = "El cuerpo no cumple la validación o el rol no coincide con el perfil comercial."),
            @ApiResponse(responseCode = "404", description = "No existe uno de los roles indicados."),
            @ApiResponse(responseCode = "409", description = "El nombre de usuario o alguno de los RUC ya está registrado."),
            @ApiResponse(responseCode = "500", description = "Ocurrió un error al crear la organización o su membresía.")
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
    @Operation(summary = "Iniciar sesión",
            description = "Verifica las credenciales y devuelve el perfil autenticado, sus membresías y un token JWT.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Credenciales aceptadas y token emitido."),
            @ApiResponse(responseCode = "400", description = "El nombre de usuario o la contraseña son incorrectos."),
            @ApiResponse(responseCode = "404", description = "No existe una cuenta con ese nombre de usuario.")
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
    @Operation(summary = "Solicitar restablecimiento de contraseña",
            description = "Inicia la recuperación de contraseña y no revela si el correo está registrado.")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Solicitud de restablecimiento aceptada."),
            @ApiResponse(responseCode = "400", description = "El correo no cumple la validación requerida.")
    })
    @PostMapping("/password-reset/request")
    public ResponseEntity<?> requestPasswordReset(@Valid @RequestBody PasswordResetRequestResource resource) {
        passwordResetService.request(resource.email());
        return ResponseEntity.accepted().body(java.util.Map.of(
                "message", "If the account exists, password reset instructions have been sent."));
    }

    /**
     * Completa el restablecimiento de contraseña con un token vigente.
     *
     * <p>Public endpoint. The token must be unexpired and unused; on success it is consumed and the
     * password is replaced by a hash of the new value.</p>
     */
    @Operation(summary = "Confirmar restablecimiento de contraseña",
            description = "Actualiza la contraseña cuando el token es válido, no ha vencido y no se ha utilizado; el token se consume una sola vez.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Contraseña actualizada sin contenido de respuesta."),
            @ApiResponse(responseCode = "400", description = "El token falta, venció o ya se usó, o la nueva contraseña no es válida.")
    })
    @PostMapping("/password-reset/confirm")
    public ResponseEntity<?> confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmResource resource) {
        passwordResetService.confirm(resource.token(), resource.newPassword());
        return ResponseEntity.noContent().build();
    }
}
