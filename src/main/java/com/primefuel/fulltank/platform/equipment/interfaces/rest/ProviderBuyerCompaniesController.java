package com.primefuel.fulltank.platform.equipment.interfaces.rest;

import com.primefuel.fulltank.platform.equipment.application.queryservices.ProviderBuyerQueryService;
import com.primefuel.fulltank.platform.equipment.domain.model.queries.GetProviderBuyerCompaniesQuery;
import com.primefuel.fulltank.platform.equipment.interfaces.rest.resources.ProviderBuyerCompanyResource;
import com.primefuel.fulltank.platform.equipment.interfaces.rest.transform.ProviderBuyerCompanyResourceFromDomainAssembler;
import com.primefuel.fulltank.platform.iam.api.TenantAccess;
import com.primefuel.fulltank.platform.shared.application.result.ApplicationError;
import com.primefuel.fulltank.platform.shared.interfaces.rest.transform.ErrorResponseAssembler;
import com.primefuel.fulltank.platform.shared.interfaces.rest.transform.ResponseEntityAssembler;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(value = "/api/provider/buyer-companies", produces = "application/json")
@Tag(name = "Clientes del distribuidor")
public class ProviderBuyerCompaniesController {
    private final ProviderBuyerQueryService queries;
    private final TenantAccess access;

    public ProviderBuyerCompaniesController(ProviderBuyerQueryService queries, TenantAccess access) {
        this.queries = queries;
        this.access = access;
    }

    @GetMapping
    @PreAuthorize("@currentUserAccess.isProvider()")
    @Operation(
            summary = "Listar compradores vinculados",
            description =
                    "Empresas con vínculo explícito, órdenes o solicitudes del proveedor"
                        + " autenticado. id es buyerCompanyId. Tanques activos; crítico significa"
                        + " nivel <= umbral de política (20% por defecto). Pedidos históricos y"
                        + " activos limitados al proveedor.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Compradores propios, o [] sin relaciones"),
        @ApiResponse(responseCode = "403", description = "Sin rol/identidad de proveedor")
    })
    public ResponseEntity<List<ProviderBuyerCompanyResource>> list() {
        var provider = access.currentProviderId();
        if (provider.isEmpty())
            throw new AccessDeniedException("Provider identity or ownership required");
        return ResponseEntity.ok(
                queries.handle(new GetProviderBuyerCompaniesQuery(provider.get())).stream()
                        .map(ProviderBuyerCompanyResourceFromDomainAssembler::toResourceFromDomain)
                        .toList());
    }

}
