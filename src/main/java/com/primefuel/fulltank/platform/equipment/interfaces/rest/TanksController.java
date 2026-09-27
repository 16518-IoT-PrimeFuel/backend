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
@RequestMapping(value = "/api/v2/tanks", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Cisternas", description = "Activos de almacenamiento y configuración de cisternas")
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
     * Registra una cisterna para una cuenta de cliente de la organización activa.
     *
     * <p>La organización se obtiene de la identidad autenticada. La cuenta y el sitio deben pertenecer a ella; la capacidad y el nivel inicial deben respetar sus invariantes.</p>
     */
    @Operation(summary = "Registrar cisterna",
            description = "Crea una cisterna para una cuenta y un sitio de la organización activa, y valida capacidad, unidad y nivel inicial.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Cisterna creada."),
            @ApiResponse(responseCode = "400", description = "El cuerpo es inválido o la cuenta, el sitio o la capacidad incumplen las reglas del dominio."),
            @ApiResponse(responseCode = "403", description = "El usuario no está autenticado o no tiene una organización activa."),
            @ApiResponse(responseCode = "409", description = "El equipo heredado indicado ya está asociado a una cisterna.")
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
     * Lista las cisternas de la organización activa.
     *
     * <p>El tenant se deriva de la membresía autenticada y no de parámetros enviados por el cliente.</p>
     */
    @Operation(summary = "Listar cisternas",
            description = "Devuelve las cisternas asociadas a la organización activa del usuario.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cisternas devueltas."),
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
     * Consulta una cisterna por identificador.
     *
     * <p>La cisterna debe pertenecer a la organización activa; las de otro tenant responden como no encontradas.</p>
     */
    @Operation(summary = "Consultar cisterna por identificador",
            description = "Devuelve la cisterna indicada únicamente cuando pertenece a la organización activa del usuario.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cisterna devuelta."),
            @ApiResponse(responseCode = "403", description = "El usuario no está autenticado o no tiene una organización activa."),
            @ApiResponse(responseCode = "404", description = "La cisterna no existe o pertenece a otra organización.")
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
