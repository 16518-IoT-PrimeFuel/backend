package com.primefuel.fulltank.platform.equipment.interfaces.rest;

import com.primefuel.fulltank.platform.equipment.application.queryservices.ProviderTankQueryService;
import com.primefuel.fulltank.platform.equipment.domain.model.queries.GetProviderTanksQuery;
import com.primefuel.fulltank.platform.equipment.domain.model.queries.GetProviderTankByIdQuery;
import com.primefuel.fulltank.platform.equipment.interfaces.rest.resources.ProviderTankResource;
import com.primefuel.fulltank.platform.equipment.interfaces.rest.transform.ProviderTankResourceFromDomainAssembler;
import com.primefuel.fulltank.platform.iam.api.TenantAccess;
import com.primefuel.fulltank.platform.shared.interfaces.rest.transform.ResponseEntityAssembler;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.constraints.Positive;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value = "/api/provider/tanks", produces = "application/json")
@Tag(name = "Tanques del distribuidor")
public class ProviderTanksController {
    private final ProviderTankQueryService queries;
    private final TenantAccess access;

    public ProviderTanksController(ProviderTankQueryService queries, TenantAccess access) {
        this.queries = queries;
        this.access = access;
    }

    @GetMapping("/{tankId}")
    @PreAuthorize("@currentUserAccess.isProvider()")
    @Operation(operationId = "getProviderTank", summary = "Consultar tanque vinculado",
            description = "Mismo recurso que el listado. Solo tanques activos de compradores con"
                    + " vínculo explícito, pedidos o solicitudes del proveedor autenticado.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Detalle del tanque",
                content = @Content(schema = @Schema(implementation = ProviderTankResource.class))),
        @ApiResponse(responseCode = "400", description = "Id inválido"),
        @ApiResponse(responseCode = "403", description = "Sin proveedor autenticado"),
        @ApiResponse(responseCode = "404", description = "Tanque inexistente, inactivo o ajeno")
    })
    public ResponseEntity<?> get(@PathVariable @Positive Long tankId) {
        var provider = access.currentProviderId();
        if (provider.isEmpty()) {
            throw new AccessDeniedException("Provider identity or ownership required");
        }
        return ResponseEntityAssembler.toResponseEntityFromResult(
                queries.handle(new GetProviderTankByIdQuery(provider.get(), tankId)),
                ProviderTankResourceFromDomainAssembler::toResourceFromDomain,
                HttpStatus.OK);
    }

    @GetMapping
    @PreAuthorize("@currentUserAccess.isProvider()")
    @Operation(
            summary = "Listar tanques de compradores vinculados",
            description =
                    "Tanques activos de compradores con pedidos o solicitudes del proveedor"
                        + " autenticado. buyerCompanyId usa id de buyer-companies. Nivel y"
                        + " capacidad en unit; umbral porcentual; devices contiene asociaciones IoT"
                        + " vigentes sin credenciales.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Tanques vinculados o []",
                content =
                        @Content(
                                array =
                                        @ArraySchema(
                                                schema =
                                                        @Schema(
                                                                implementation =
                                                                        ProviderTankResource
                                                                                .class)))),
        @ApiResponse(responseCode = "400", description = "Identificador inválido"),
        @ApiResponse(responseCode = "403", description = "Sin rol/identidad de proveedor"),
        @ApiResponse(
                responseCode = "404",
                description = "Comprador inexistente o sin relación comercial")
    })
    public ResponseEntity<?> list(@RequestParam(required = false) @Positive Long buyerCompanyId) {
        var provider = access.currentProviderId();
        if (provider.isEmpty())
            throw new AccessDeniedException("Provider identity or ownership required");
        return ResponseEntityAssembler.toResponseEntityFromResult(
                queries.handle(new GetProviderTanksQuery(provider.get(), buyerCompanyId)),
                rows ->
                        rows.stream()
                                .map(ProviderTankResourceFromDomainAssembler::toResourceFromDomain)
                                .toList(),
                HttpStatus.OK);
    }
}
