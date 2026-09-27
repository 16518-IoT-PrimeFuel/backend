package com.primefuel.fulltank.platform.equipment.interfaces.rest;

import com.primefuel.fulltank.platform.equipment.application.commandservices.EquipmentCommandService;
import com.primefuel.fulltank.platform.equipment.application.queryservices.EquipmentQueryService;
import com.primefuel.fulltank.platform.equipment.domain.model.queries.GetAllEquipmentQuery;
import com.primefuel.fulltank.platform.equipment.domain.model.queries.GetEquipmentByCompanyIdQuery;
import com.primefuel.fulltank.platform.equipment.domain.model.queries.GetEquipmentByIdQuery;
import com.primefuel.fulltank.platform.equipment.interfaces.rest.resources.CreateEquipmentResource;
import com.primefuel.fulltank.platform.equipment.interfaces.rest.resources.EquipmentResource;
import com.primefuel.fulltank.platform.equipment.interfaces.rest.resources.UpdateEquipmentResource;
import com.primefuel.fulltank.platform.equipment.interfaces.rest.transform.CreateEquipmentCommandFromResourceAssembler;
import com.primefuel.fulltank.platform.equipment.interfaces.rest.transform.EquipmentResourceFromEntityAssembler;
import com.primefuel.fulltank.platform.equipment.interfaces.rest.transform.UpdateEquipmentCommandFromResourceAssembler;
import com.primefuel.fulltank.platform.shared.interfaces.rest.transform.ResponseEntityAssembler;
import com.primefuel.fulltank.platform.equipment.domain.repositories.EquipmentRepository;
import com.primefuel.fulltank.platform.iam.infrastructure.authorization.sfs.services.CurrentUserAccess;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;

@RestController
@RequestMapping(value = "/api/equipment", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Equipos", description = "Gestión de equipos heredados asociados a empresas compradoras")
public class EquipmentController {

    private final EquipmentCommandService equipmentCommandService;
    private final EquipmentQueryService equipmentQueryService;
    private final EquipmentRepository equipmentRepository;
    private final CurrentUserAccess currentUserAccess;

    public EquipmentController(EquipmentCommandService equipmentCommandService,
                               EquipmentQueryService equipmentQueryService,
                               EquipmentRepository equipmentRepository,
                               CurrentUserAccess currentUserAccess) {
        this.equipmentCommandService = equipmentCommandService;
        this.equipmentQueryService = equipmentQueryService;
        this.equipmentRepository = equipmentRepository;
        this.currentUserAccess = currentUserAccess;
    }

    /**
     * Crea un equipo para la empresa del usuario.
     *
     * <p>El identificador de empresa del cuerpo debe corresponder al tenant del usuario.</p>
     */
    @Operation(summary = "Crear equipo",
            description = "Registra un equipo heredado para la empresa indicada, que debe pertenecer al usuario autenticado.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Equipo creado."),
            @ApiResponse(responseCode = "403", description = "La empresa indicada no pertenece al usuario autenticado.")
    })
    @PostMapping
    @PreAuthorize("@currentUserAccess.ownsCompany(#resource.companyId())")
    public ResponseEntity<?> createEquipment(@RequestBody CreateEquipmentResource resource) {
        var command = CreateEquipmentCommandFromResourceAssembler.toCommandFromResource(resource);
        var result = equipmentCommandService.handle(command);
        return ResponseEntityAssembler.toResponseEntityFromResult(
                result,
                EquipmentResourceFromEntityAssembler::toResourceFromEntity,
                HttpStatus.CREATED);
    }

    /**
     * Actualiza los campos de un equipo.
     *
     * <p>Solo se actualizan equipos del tenant autenticado; los ajenos responden como no encontrados. El nivel recibido también actualiza el tanque vinculada como lectura manual.</p>
     */
    @Operation(summary = "Actualizar equipo",
            description = "Aplica los campos editables al equipo de la empresa del usuario y sincroniza el nivel con el tanque vinculada cuando corresponde.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Equipo actualizado."),
            @ApiResponse(responseCode = "404", description = "El equipo no existe o pertenece a otra empresa.")
    })
    @PostMapping("/{equipmentId}/update")
    public ResponseEntity<?> updateEquipment(@PathVariable Long equipmentId,
                                             @RequestBody UpdateEquipmentResource resource) {
        var existing = equipmentRepository.findById(equipmentId).orElse(null);
        if (existing == null || !currentUserAccess.ownsCompany(existing.getCompanyId())) {
            return ResponseEntity.notFound().build();
        }
        var command = UpdateEquipmentCommandFromResourceAssembler.toCommandFromResource(equipmentId, resource);
        var result = equipmentCommandService.handle(command);
        return ResponseEntityAssembler.toResponseEntityFromResult(
                result,
                EquipmentResourceFromEntityAssembler::toResourceFromEntity,
                HttpStatus.OK);
    }

    /**
     * Lista todos los equipos registrados en la plataforma.
     *
     * <p>Requiere la autoridad administrativa ROLE_ADMIN.</p>
     */
    @Operation(summary = "Listar todos los equipos",
            description = "Devuelve los equipos de todas las empresas; requiere la autoridad ROLE_ADMIN.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Equipos devueltos."),
            @ApiResponse(responseCode = "403", description = "El usuario no tiene la autoridad ROLE_ADMIN.")
    })
    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<List<EquipmentResource>> getAllEquipment() {
        var equipment = equipmentQueryService.handle(new GetAllEquipmentQuery());
        var resources = equipment.stream().map(EquipmentResourceFromEntityAssembler::toResourceFromEntity).toList();
        return new ResponseEntity<>(resources, HttpStatus.OK);
    }

    /**
     * Consulta un equipo por identificador.
     *
     * <p>Solo la empresa propietaria puede consultarlo; los equipos ajenos se informan como no encontrados.</p>
     */
    @Operation(summary = "Consultar equipo por identificador",
            description = "Devuelve el equipo indicado si pertenece a la empresa del usuario autenticado.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Equipo devuelto."),
            @ApiResponse(responseCode = "404", description = "El equipo no existe o pertenece a otra empresa.")
    })
    @GetMapping("/{equipmentId}")
    public ResponseEntity<EquipmentResource> getEquipmentById(@PathVariable Long equipmentId) {
        var result = equipmentQueryService.handle(new GetEquipmentByIdQuery(equipmentId))
                .filter(equipment -> currentUserAccess.ownsCompany(equipment.getCompanyId()));
        return result.map(e -> new ResponseEntity<>(
                        EquipmentResourceFromEntityAssembler.toResourceFromEntity(e), HttpStatus.OK))
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    /**
     * Lista los equipos de una empresa.
     *
     * <p>Solo la empresa propietaria puede consultar esta colección.</p>
     */
    @Operation(summary = "Listar equipos por empresa",
            description = "Devuelve los equipos de la empresa indicada si coincide con el tenant del usuario.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Equipos devueltos."),
            @ApiResponse(responseCode = "403", description = "La empresa solicitada no pertenece al usuario.")
    })
    @GetMapping("/company/{companyId}")
    @PreAuthorize("@currentUserAccess.ownsCompany(#companyId)")
    public ResponseEntity<List<EquipmentResource>> getEquipmentByCompany(@PathVariable Long companyId) {
        var equipment = equipmentQueryService.handle(new GetEquipmentByCompanyIdQuery(companyId));
        var resources = equipment.stream().map(EquipmentResourceFromEntityAssembler::toResourceFromEntity).toList();
        return new ResponseEntity<>(resources, HttpStatus.OK);
    }
}
