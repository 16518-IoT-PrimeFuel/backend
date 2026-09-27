package com.primefuel.fulltank.platform.replenishment.interfaces.rest;

import com.primefuel.fulltank.platform.equipment.api.TankAssets;
import com.primefuel.fulltank.platform.iam.api.MembershipAccess;
import com.primefuel.fulltank.platform.replenishment.application.commandservices.RefillPolicyCommandService;
import com.primefuel.fulltank.platform.replenishment.application.queryservices.RefillPolicyQueryService;
import com.primefuel.fulltank.platform.replenishment.domain.model.commands.ConfigureRefillPolicyCommand;
import com.primefuel.fulltank.platform.replenishment.domain.model.queries.GetRefillEpisodesByTankQuery;
import com.primefuel.fulltank.platform.replenishment.domain.model.queries.GetRefillPolicyByTankQuery;
import com.primefuel.fulltank.platform.replenishment.interfaces.rest.resources.ConfigureRefillPolicyResource;
import com.primefuel.fulltank.platform.replenishment.interfaces.rest.resources.RefillEpisodeResource;
import com.primefuel.fulltank.platform.replenishment.interfaces.rest.resources.RefillPolicyResource;
import com.primefuel.fulltank.platform.replenishment.interfaces.rest.transform.RefillEpisodeResourceFromDomainAssembler;
import com.primefuel.fulltank.platform.replenishment.interfaces.rest.transform.RefillPolicyResourceFromDomainAssembler;
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

/**
 * Política de reposición de cada tanque (S09) y consulta de decisiones simuladas. La automatización se activa de forma explícita mediante {@code autoGenerateEnabled}.
 */
@RestController
@RequestMapping(value = "/api/tanks/{tankId}", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Políticas de reposición", description = "Configuración por tanque y consulta de episodios de abastecimiento")
public class RefillPoliciesController {

    private final RefillPolicyCommandService commandService;
    private final RefillPolicyQueryService queryService;
    private final MembershipAccess membershipAccess;
    private final TankAssets tankAssets;

    public RefillPoliciesController(RefillPolicyCommandService commandService,
                                    RefillPolicyQueryService queryService,
                                    MembershipAccess membershipAccess,
                                    TankAssets tankAssets) {
        this.commandService = commandService;
        this.queryService = queryService;
        this.membershipAccess = membershipAccess;
        this.tankAssets = tankAssets;
    }

    /**
     * Crea o reconfigura la política de reposición de un tanque.
     *
     * <p>El tanque debe pertenecer a la organización activa. Los campos omitidos usan valores globales aprobados. La propiedad se comprueba antes de configurar la política.</p>
     */
    @Operation(summary = "Configurar política de reposición",
            description = "Crea o actualiza la política de el tanque propia. Los campos opcionales sobrescriben los valores globales aprobados.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Política de reposición creada o actualizada."),
            @ApiResponse(responseCode = "400", description = "El cuerpo no cumple las validaciones o un valor de política no es válido."),
            @ApiResponse(responseCode = "403", description = "El usuario no tiene organización activa o el tanque pertenece a otro tenant.")
    })
    @PutMapping("/refill-policy")
    public ResponseEntity<?> configure(@PathVariable Long tankId,
                                       @Valid @RequestBody ConfigureRefillPolicyResource resource) {
        var organizationId = membershipAccess.currentOrganizationId();
        if (organizationId.isEmpty() || !ownsTank(tankId, organizationId.get())) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }
        var result = commandService.handle(new ConfigureRefillPolicyCommand(
                tankId, organizationId.get(), resource.lowLevelPercent(), resource.hysteresisPercent(),
                resource.targetLevelPercent(), resource.providerId(), resource.fuelProductId(),
                resource.autoGenerateEnabled()));
        return ResponseEntityAssembler.toResponseEntityFromResult(
                result, RefillPolicyResourceFromDomainAssembler::toResourceFromDomain, HttpStatus.OK);
    }

    /**
     * Consulta la política de reposición de un tanque.
     *
     * <p>El tanque debe pertenecer a la organización activa; si todavía no tiene política, responde como no encontrada.</p>
     */
    @Operation(summary = "Consultar política de reposición",
            description = "Devuelve la política vigente de un tanque que pertenece a la organización activa.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Política de reposición devuelta."),
            @ApiResponse(responseCode = "403", description = "El usuario no tiene organización activa o no es propietario de el tanque."),
            @ApiResponse(responseCode = "404", description = "El tanque todavía no tiene una política configurada.")
    })
    @GetMapping("/refill-policy")
    public ResponseEntity<RefillPolicyResource> get(@PathVariable Long tankId) {
        var organizationId = membershipAccess.currentOrganizationId();
        if (organizationId.isEmpty() || !ownsTank(tankId, organizationId.get())) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }
        return queryService.handle(new GetRefillPolicyByTankQuery(tankId))
                .map(policy -> new ResponseEntity<>(
                        RefillPolicyResourceFromDomainAssembler.toResourceFromDomain(policy), HttpStatus.OK))
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    /**
     * Lista los episodios de reposición de un tanque.
     *
     * <p>El tanque debe pertenecer a la organización activa; los episodios corresponden a las decisiones registradas por el evaluador.</p>
     */
    @Operation(summary = "Listar episodios de reposición",
            description = "Devuelve los episodios registrados para un tanque propia de la organización activa.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Episodios de reposición devueltos."),
            @ApiResponse(responseCode = "403", description = "El usuario no tiene organización activa o no es propietario de el tanque.")
    })
    @GetMapping("/refill-episodes")
    public ResponseEntity<List<RefillEpisodeResource>> episodes(@PathVariable Long tankId) {
        var organizationId = membershipAccess.currentOrganizationId();
        if (organizationId.isEmpty() || !ownsTank(tankId, organizationId.get())) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }
        var episodes = queryService.handle(new GetRefillEpisodesByTankQuery(tankId));
        return new ResponseEntity<>(episodes.stream()
                .map(RefillEpisodeResourceFromDomainAssembler::toResourceFromDomain).toList(), HttpStatus.OK);
    }

    private boolean ownsTank(Long tankId, Long organizationId) {
        return tankAssets.findById(tankId)
                .map(snapshot -> organizationId.equals(snapshot.organizationId()))
                .orElse(false);
    }
}
