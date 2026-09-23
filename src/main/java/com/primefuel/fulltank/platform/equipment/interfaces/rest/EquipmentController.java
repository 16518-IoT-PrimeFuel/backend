package com.primefuel.fulltank.platform.equipment.interfaces.rest;

import com.primefuel.fulltank.platform.equipment.application.commandservices.EquipmentCommandService;
import com.primefuel.fulltank.platform.equipment.application.queryservices.EquipmentQueryService;
import com.primefuel.fulltank.platform.equipment.domain.model.queries.GetAllEquipmentQuery;
import com.primefuel.fulltank.platform.equipment.domain.model.queries.GetEquipmentByCompanyIdQuery;
import com.primefuel.fulltank.platform.equipment.domain.model.queries.GetEquipmentByIdQuery;
import com.primefuel.fulltank.platform.equipment.interfaces.rest.resources.CreateEquipmentResource;
import com.primefuel.fulltank.platform.equipment.interfaces.rest.resources.EquipmentResource;
import com.primefuel.fulltank.platform.equipment.interfaces.rest.resources.FavoriteProviderResource;
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
@RequestMapping(value = "/api/v1/equipment", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Equipment", description = "Equipment management endpoints")
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
     * Sets the favorite provider of a piece of equipment.
     *
     * <p>Only a buyer may call this, and only for equipment that belongs to its own company; a piece
     * of equipment owned by another company is reported as not found.</p>
     */
    @Operation(summary = "Assign a favorite provider to equipment",
            description = "Sets the preferred provider for the given equipment owned by the caller's company.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Favorite provider assigned."),
            @ApiResponse(responseCode = "403", description = "Caller does not hold the buyer role."),
            @ApiResponse(responseCode = "404", description = "Equipment does not exist or belongs to another company.")
    })
    @PostMapping("/{equipmentId}/favorite-provider")
    @PreAuthorize("@currentUserAccess.isBuyerRole()")
    public ResponseEntity<EquipmentResource> assignFavoriteProvider(
            @PathVariable Long equipmentId, @RequestBody FavoriteProviderResource resource) {
        return equipmentRepository.findById(equipmentId)
                .filter(equipment -> currentUserAccess.ownsCompany(equipment.getCompanyId()))
                .map(equipment -> {
                    equipment.assignFavoriteProvider(resource.providerId());
                    var saved = equipmentRepository.save(equipment);
                    return ResponseEntity.ok(EquipmentResourceFromEntityAssembler.toResourceFromEntity(saved));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Creates a piece of equipment for the caller's company.
     *
     * <p>The company id in the body must match the caller's own company.</p>
     */
    @Operation(summary = "Create equipment",
            description = "Creates equipment for the caller's company; the supplied companyId must be the caller's own.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Equipment created."),
            @ApiResponse(responseCode = "403", description = "Caller does not own the company in the request body.")
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
     * Updates a piece of equipment.
     *
     * <p>The caller must own the equipment; other companies (including existing equipment of another
     * tenant) are answered as not found. A level supplied here is mirrored to a mapped tank as a
     * manual reading.</p>
     */
    @Operation(summary = "Update equipment",
            description = "Applies field changes to the given equipment when it belongs to the caller's company.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Equipment updated."),
            @ApiResponse(responseCode = "404", description = "Equipment does not exist or belongs to another company.")
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
     * Lists every piece of equipment in the platform.
     *
     * <p>Administrative endpoint; restricted to callers holding the ROLE_ADMIN authority.</p>
     */
    @Operation(summary = "List all equipment",
            description = "Returns every registered piece of equipment. Restricted to administrators.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Equipment returned."),
            @ApiResponse(responseCode = "403", description = "Caller does not hold the ROLE_ADMIN authority.")
    })
    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<List<EquipmentResource>> getAllEquipment() {
        var equipment = equipmentQueryService.handle(new GetAllEquipmentQuery());
        var resources = equipment.stream().map(EquipmentResourceFromEntityAssembler::toResourceFromEntity).toList();
        return new ResponseEntity<>(resources, HttpStatus.OK);
    }

    /**
     * Retrieves a single piece of equipment.
     *
     * <p>Only the owning company may read the record; anything else is reported as not found.</p>
     */
    @Operation(summary = "Get equipment by id",
            description = "Returns the equipment identified by the path id when it belongs to the caller's company.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Equipment returned."),
            @ApiResponse(responseCode = "404", description = "Equipment does not exist or belongs to another company.")
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
     * Lists the equipment of a specific company.
     *
     * <p>Only the owning company may list its own equipment.</p>
     */
    @Operation(summary = "List equipment by company",
            description = "Returns all equipment of the given company when it matches the caller's own company.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Equipment returned."),
            @ApiResponse(responseCode = "403", description = "Caller does not own the requested company.")
    })
    @GetMapping("/company/{companyId}")
    @PreAuthorize("@currentUserAccess.ownsCompany(#companyId)")
    public ResponseEntity<List<EquipmentResource>> getEquipmentByCompany(@PathVariable Long companyId) {
        var equipment = equipmentQueryService.handle(new GetEquipmentByCompanyIdQuery(companyId));
        var resources = equipment.stream().map(EquipmentResourceFromEntityAssembler::toResourceFromEntity).toList();
        return new ResponseEntity<>(resources, HttpStatus.OK);
    }
}
