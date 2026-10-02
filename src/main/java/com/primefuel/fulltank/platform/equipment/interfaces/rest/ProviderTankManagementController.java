package com.primefuel.fulltank.platform.equipment.interfaces.rest;

import com.primefuel.fulltank.platform.equipment.application.commandservices.ProviderTankCommandService;
import com.primefuel.fulltank.platform.equipment.application.queryservices.ProviderTankQueryService;
import com.primefuel.fulltank.platform.equipment.domain.model.commands.*;
import com.primefuel.fulltank.platform.equipment.domain.model.queries.GetProviderTanksQuery;
import com.primefuel.fulltank.platform.equipment.interfaces.rest.resources.*;
import com.primefuel.fulltank.platform.equipment.interfaces.rest.transform.ProviderTankResourceFromDomainAssembler;
import com.primefuel.fulltank.platform.iam.api.TenantAccess;
import com.primefuel.fulltank.platform.shared.application.result.*;
import com.primefuel.fulltank.platform.shared.interfaces.rest.transform.ErrorResponseAssembler;
import com.primefuel.fulltank.platform.shared.interfaces.rest.transform.ResponseEntityAssembler;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

import org.springframework.http.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(value = "/api/provider/tanks", produces = "application/json")
@PreAuthorize("@currentUserAccess.isProvider()")
@Tag(name = "Tanques del distribuidor")
public class ProviderTankManagementController {
    private final ProviderTankCommandService commands;
    private final ProviderTankQueryService queries;
    private final TenantAccess access;

    public ProviderTankManagementController(
            ProviderTankCommandService commands,
            ProviderTankQueryService queries,
            TenantAccess access) {
        this.commands = commands;
        this.queries = queries;
        this.access = access;
    }

    @PostMapping
    @Operation(
            summary = "Asociar tanque, producto y dispositivo",
            description =
                    "Flujo principal US-51. Comprador vinculado comercialmente; cuenta y sitio de"
                        + " su organización; producto activo propio. Guarda tanque, política y"
                        + " binding IoT en una transacción. autoGenerateEnabled por defecto true."
                        + " El dispositivo aún requiere credencial técnica X-Device-Token.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "201",
                description = "Tanque asociado",
                content = @Content(schema = @Schema(implementation = ProviderTankResource.class))),
        @ApiResponse(
                responseCode = "400",
                description = "Cuerpo, capacidad, unidad o umbral inválido"),
        @ApiResponse(responseCode = "403", description = "Sin proveedor autenticado"),
        @ApiResponse(responseCode = "404", description = "Comprador no vinculado o producto ajeno"),
        @ApiResponse(responseCode = "409", description = "Dispositivo duplicado")
    })
    public ResponseEntity<?> create(@Valid @RequestBody RegisterProviderTankResource body) {
        var provider = access.currentProviderId();
        if (provider.isEmpty())
            throw new AccessDeniedException("Provider identity or ownership required");
        var result =
                commands.handle(
                        new RegisterProviderTankCommand(
                                provider.get(),
                                body.buyerCompanyId(),
                                body.customerAccountId(),
                                body.siteId(),
                                body.name(),
                                body.fuelProductId(),
                                body.capacity(),
                                body.unit(),
                                body.initialLevel(),
                                body.lowLevelPercent(),
                                body.deviceId(),
                                body.channel(),
                                body.autoGenerateEnabled() == null || body.autoGenerateEnabled()));
        return respond(result, provider.get(), HttpStatus.CREATED);
    }

    private ResponseEntity<?> respond(
            Result<Long, ApplicationError> result, Long provider, HttpStatus status) {
        return ResponseEntityAssembler.toResponseEntityFromResult(
                result,
                id ->
                        queries
                                .handle(new GetProviderTanksQuery(provider, null))
                                .getOrElse(List.of())
                                .stream()
                                .filter(t -> id.equals(t.id()))
                                .map(ProviderTankResourceFromDomainAssembler::toResourceFromDomain)
                                .findFirst()
                                .orElse(null),
                status);
    }
}
