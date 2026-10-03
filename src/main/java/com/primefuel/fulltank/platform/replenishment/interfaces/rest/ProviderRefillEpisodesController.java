package com.primefuel.fulltank.platform.replenishment.interfaces.rest;

import com.primefuel.fulltank.platform.equipment.api.ProviderBuyerAccess;
import com.primefuel.fulltank.platform.equipment.api.TankAssets;
import com.primefuel.fulltank.platform.iam.api.TenantAccess;
import com.primefuel.fulltank.platform.replenishment.application.queryservices.RefillPolicyQueryService;
import com.primefuel.fulltank.platform.replenishment.domain.model.queries.GetRefillEpisodesByTankQuery;
import com.primefuel.fulltank.platform.replenishment.interfaces.rest.resources.RefillEpisodeResource;
import com.primefuel.fulltank.platform.replenishment.interfaces.rest.transform.RefillEpisodeResourceFromDomainAssembler;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.constraints.Positive;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(
        value = "/api/provider/tanks/{tankId}/refill-episodes",
        produces = "application/json")
@Tag(name = "Políticas de reposición")
public class ProviderRefillEpisodesController {
    private final TenantAccess access;
    private final ProviderBuyerAccess buyers;
    private final TankAssets tanks;
    private final RefillPolicyQueryService queries;

    public ProviderRefillEpisodesController(
            TenantAccess access,
            ProviderBuyerAccess buyers,
            TankAssets tanks,
            RefillPolicyQueryService queries) {
        this.access = access;
        this.buyers = buyers;
        this.tanks = tanks;
        this.queries = queries;
    }

    @GetMapping
    @PreAuthorize("@currentUserAccess.isProvider()")
    @Operation(
            summary = "Consultar episodios de reposición como distribuidor",
            description =
                    "Mismo contrato que la ruta del propietario; exige pedido o solicitud entre el"
                        + " proveedor autenticado y el comprador dueño del tanque. Lista vacía sin"
                        + " historial.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Episodios del tanque vinculado o []"),
        @ApiResponse(responseCode = "400", description = "Identificador inválido"),
        @ApiResponse(responseCode = "403", description = "Sin rol/identidad de proveedor"),
        @ApiResponse(
                responseCode = "404",
                description = "Tanque inexistente o comprador sin relación")
    })
    public ResponseEntity<List<RefillEpisodeResource>> list(@PathVariable @Positive Long tankId) {
        var provider = access.currentProviderId();
        if (provider.isEmpty())
            throw new AccessDeniedException("Provider identity or ownership required");
        if (!buyers.canReadTank(provider.get(), tankId)) return ResponseEntity.notFound().build();
        var tank = tanks.findById(tankId);
        if (tank.isEmpty()) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(
                queries.handle(new GetRefillEpisodesByTankQuery(tankId)).stream()
                        .filter(e -> tank.get().organizationId().equals(e.getOrganizationId()))
                        .map(RefillEpisodeResourceFromDomainAssembler::toResourceFromDomain)
                        .toList());
    }
}
