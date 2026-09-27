package com.primefuel.fulltank.platform.equipment.interfaces.rest;

import com.primefuel.fulltank.platform.equipment.application.commandservices.TankCommandService;
import com.primefuel.fulltank.platform.equipment.application.queryservices.TankQueryService;
import com.primefuel.fulltank.platform.equipment.domain.model.commands.RegisterTankCommand;
import com.primefuel.fulltank.platform.equipment.domain.model.queries.GetTanksByOrganizationQuery;
import com.primefuel.fulltank.platform.equipment.interfaces.rest.resources.RegisterTankResource;
import com.primefuel.fulltank.platform.equipment.interfaces.rest.resources.TankResource;
import com.primefuel.fulltank.platform.equipment.interfaces.rest.transform.TankResourceFromDomainAssembler;
import com.primefuel.fulltank.platform.iam.api.MembershipAccess;
import com.primefuel.fulltank.platform.shared.interfaces.rest.transform.ResponseEntityAssembler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(value = "/api/tanks", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Tanques", description = "Activos de almacenamiento y configuración de tanques")
public class TanksController {

    private final TankCommandService tankCommandService;
    private final TankQueryService tankQueryService;
    private final MembershipAccess membershipAccess;

    public TanksController(TankCommandService tankCommandService,
                           TankQueryService tankQueryService,
                           MembershipAccess membershipAccess) {
        this.tankCommandService = tankCommandService;
        this.tankQueryService = tankQueryService;
        this.membershipAccess = membershipAccess;
    }

    /**
     * Registra un tanque para una cuenta de cliente de la organización activa.
     *
     * <p>La organización se obtiene de la identidad autenticada. La cuenta y el sitio deben pertenecer a ella; la capacidad y el nivel inicial deben respetar sus invariantes.</p>
     */
    @Operation(summary = "Registrar tanque",
            description = "Crea un tanque para una cuenta y un sitio de la organización activa, y valida capacidad, unidad y nivel inicial.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Tanque creado."),
            @ApiResponse(responseCode = "400", description = "El cuerpo es inválido o la cuenta, el sitio o la capacidad incumplen las reglas del dominio."),
            @ApiResponse(responseCode = "403", description = "El usuario no está autenticado o no tiene una organización activa."),
            @ApiResponse(responseCode = "409", description = "El equipo heredado indicado ya está asociado a un tanque.")
    })
    @PostMapping
    public ResponseEntity<?> registerTank(@Valid @RequestBody RegisterTankResource resource) {
        var organizationId = membershipAccess.currentOrganizationId();
        if (organizationId.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }
        var result = tankCommandService.handle(new RegisterTankCommand(
                organizationId.get(), resource.customerAccountId(), resource.siteId(), resource.name(),
                resource.fuelType(), resource.capacity(), resource.unit(), resource.initialLevel(), null));
        return ResponseEntityAssembler.toResponseEntityFromResult(
                result, TankResourceFromDomainAssembler::toResourceFromDomain, HttpStatus.CREATED);
    }

    /**
     * Lista los tanques de la organización activa.
     *
     * <p>El tenant se deriva de la membresía autenticada y no de parámetros enviados por el cliente.</p>
     */
    @Operation(summary = "Listar tanques",
            description = "Devuelve los tanques asociados a la organización activa del usuario.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tanques devueltos."),
            @ApiResponse(responseCode = "403", description = "El usuario no está autenticado o no tiene una organización activa.")
    })
    @GetMapping
    public ResponseEntity<List<TankResource>> listTanks() {
        var organizationId = membershipAccess.currentOrganizationId();
        if (organizationId.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }
        var tanks = tankQueryService.handle(new GetTanksByOrganizationQuery(organizationId.get()));
        return new ResponseEntity<>(
                tanks.stream().map(TankResourceFromDomainAssembler::toResourceFromDomain).toList(),
                HttpStatus.OK);
    }

    /**
     * Consulta un tanque por identificador.
     *
     * <p>El tanque debe pertenecer a la organización activa; los de otro tenant responden como no encontrados.</p>
     */
    @Operation(summary = "Consultar tanque por identificador",
            description = "Devuelve el tanque indicado únicamente cuando pertenece a la organización activa del usuario.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tanque devuelto."),
            @ApiResponse(responseCode = "403", description = "El usuario no está autenticado o no tiene una organización activa."),
            @ApiResponse(responseCode = "404", description = "El tanque no existe o pertenece a otra organización.")
    })
    @GetMapping("/{tankId}")
    public ResponseEntity<TankResource> getTank(@PathVariable Long tankId) {
        var organizationId = membershipAccess.currentOrganizationId();
        if (organizationId.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }
        return tankQueryService.handle(new com.primefuel.fulltank.platform.equipment.domain.model.queries.GetTankByIdQuery(tankId))
                .filter(tank -> organizationId.get().equals(tank.getOrganizationId()))
                .map(tank -> new ResponseEntity<>(
                        TankResourceFromDomainAssembler.toResourceFromDomain(tank), HttpStatus.OK))
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }
}
