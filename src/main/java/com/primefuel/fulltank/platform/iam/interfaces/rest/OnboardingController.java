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
@RequestMapping(value = "/api/onboarding", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Registro de organizaciones", description = "Alta de organizaciones y asignación de su propietario inicial")
public class OnboardingController {

    private final OnboardingCommandService onboardingCommandService;
    private final MembershipAccess membershipAccess;

    public OnboardingController(OnboardingCommandService onboardingCommandService,
                                MembershipAccess membershipAccess) {
        this.onboardingCommandService = onboardingCommandService;
        this.membershipAccess = membershipAccess;
    }

    /**
     * Crea una organización cuyo propietario es el usuario autenticado.
     *
     * <p>El propietario se toma del principal, no del cuerpo. El tipo debe estar admitido y el RUC ser único;
     * al creador se le asigna una membresía OWNER.</p>
     */
    @Operation(summary = "Registrar organización",
            description = "Crea una organización para el usuario autenticado y le asigna la membresía OWNER.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Organización creada y propiedad asignada."),
            @ApiResponse(responseCode = "400", description = "El cuerpo no supera la validación, el tipo no está admitido o no se pudo resolver al propietario."),
            @ApiResponse(responseCode = "403", description = "El usuario no está autenticado."),
            @ApiResponse(responseCode = "409", description = "Ya existe una organización con el mismo RUC o no se pudo asignar la propiedad.")
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
