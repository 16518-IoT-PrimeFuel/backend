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
@RequestMapping(value = "/api", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Invitaciones", description = "Invitación y administración de membresías de organización")
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
     * Invita una dirección de correo a una organización.
     *
     * <p>Solo OWNER o ADMIN de la organización pueden invitar, y OWNER no es invitable. No se duplican invitaciones pendientes al mismo correo;
     * el rol debe estar admitido y el token es de un solo uso y tiene vigencia limitada.</p>
     */
    @Operation(summary = "Invitar miembro a una organización",
            description = "Crea una invitación pendiente para el correo y rol (ADMIN o MEMBER) indicados en la organización de la ruta. Requiere ser OWNER o ADMIN de esa organización.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Invitación creada."),
            @ApiResponse(responseCode = "400", description = "El cuerpo no supera la validación o el rol no está admitido (OWNER no es invitable)."),
            @ApiResponse(responseCode = "403", description = "El usuario no es OWNER ni ADMIN de la organización indicada."),
            @ApiResponse(responseCode = "404", description = "La organización no existe."),
            @ApiResponse(responseCode = "409", description = "Ya existe una invitación pendiente para este correo.")
    })
    @PostMapping("/organizations/{organizationId}/invitations")
    @PreAuthorize("@membershipAccess.canManageOrganization(#organizationId)")
    public ResponseEntity<?> invite(@PathVariable Long organizationId,
                                    @Valid @RequestBody InviteMemberResource resource) {
        MembershipRole role;
        try {
            role = MembershipRole.valueOf(resource.role().trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest()
                    .body(ApplicationError.validationError("role", "Unknown membership role"));
        }
        if (role == MembershipRole.OWNER) { // la propiedad no se cede por invitación
            return ResponseEntity.badRequest()
                    .body(ApplicationError.validationError("role", "OWNER cannot be invited"));
        }
        var result = invitationCommandService.handle(new InviteMemberCommand(
                organizationId, resource.email(), role, membershipAccess.currentUserId().orElse(null)));
        return ResponseEntityAssembler.toResponseEntityFromResult(
                result, InvitationResourceFromDomainAssembler::toResourceFromDomain, HttpStatus.CREATED);
    }

    /**
     * Acepta una invitación pendiente para el usuario autenticado.
     *
     * <p>El token debe estar vigente, pendiente y no revocado. Al aceptarlo, se concede el rol invitado como membresía activa.</p>
     */
    @Operation(summary = "Aceptar invitación",
            description = "Acepta la invitación identificada por el token y concede la membresía al usuario autenticado.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Invitación aceptada y membresía concedida."),
            @ApiResponse(responseCode = "403", description = "El usuario no está autenticado."),
            @ApiResponse(responseCode = "404", description = "No existe una invitación asociada al token."),
            @ApiResponse(responseCode = "409", description = "Se produjo un conflicto al conceder la membresía."),
            @ApiResponse(responseCode = "422", description = "La invitación venció, ya fue aceptada o fue revocada.")
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
     * Revoca una invitación pendiente.
     *
     * <p>Solo los miembros de la organización pueden revocarla y únicamente mientras esté pendiente.</p>
     */
    @Operation(summary = "Revocar invitación",
            description = "Revoca una invitación pendiente de una organización a la que pertenece el usuario autenticado.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Invitación revocada."),
            @ApiResponse(responseCode = "403", description = "El usuario no es OWNER ni ADMIN de la organización de la invitación."),
            @ApiResponse(responseCode = "404", description = "La invitación no existe."),
            @ApiResponse(responseCode = "409", description = "La invitación no está pendiente y no puede revocarse.")
    })
    @DeleteMapping("/invitations/{invitationId}")
    public ResponseEntity<?> revoke(@PathVariable Long invitationId) {
        var existing = invitationQueryService.handle(new GetInvitationByIdQuery(invitationId));
        if (existing.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        if (!membershipAccess.canManageOrganization(existing.get().getOrganizationId())) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }
        var result = invitationCommandService.handle(new RevokeInvitationCommand(invitationId));
        return ResponseEntityAssembler.toResponseEntityFromResult(
                result, InvitationResourceFromDomainAssembler::toResourceFromDomain, HttpStatus.OK);
    }
}
