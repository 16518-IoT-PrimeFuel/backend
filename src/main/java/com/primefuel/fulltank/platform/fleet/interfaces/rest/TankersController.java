package com.primefuel.fulltank.platform.fleet.interfaces.rest;

import com.primefuel.fulltank.platform.fleet.api.EligibilityQuery;
import com.primefuel.fulltank.platform.fleet.api.FleetCatalog;
import com.primefuel.fulltank.platform.fleet.api.FleetRegistry;
import com.primefuel.fulltank.platform.fleet.domain.model.commands.RegisterTankerCommand;
import com.primefuel.fulltank.platform.fleet.domain.model.commands.UpdateTankerCommand;
import com.primefuel.fulltank.platform.fleet.interfaces.rest.resources.EligibilityResource;
import com.primefuel.fulltank.platform.fleet.interfaces.rest.resources.TankerInputResource;
import com.primefuel.fulltank.platform.fleet.interfaces.rest.resources.TankerResource;
import com.primefuel.fulltank.platform.iam.api.TenantAccess;
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
 * Catálogo de cisternas. El distribuidor se obtiene del principal y la desactivación conserva el registro.
 */
@RestController
@RequestMapping(value = "/api/tankers", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Cisternas de flota", description = "Administración y ciclo de vida de cisternas por distribuidor")
public class TankersController {

    private final FleetCatalog fleetCatalog;
    private final FleetRegistry fleetRegistry;
    private final EligibilityQuery eligibilityQuery;
    private final TenantAccess tenantAccess;

    public TankersController(FleetCatalog fleetCatalog,
                               FleetRegistry fleetRegistry,
                               EligibilityQuery eligibilityQuery,
                               TenantAccess tenantAccess) {
        this.fleetCatalog = fleetCatalog;
        this.fleetRegistry = fleetRegistry;
        this.eligibilityQuery = eligibilityQuery;
        this.tenantAccess = tenantAccess;
    }

    /**
     * Registra una cisterna para el distribuidor autenticado.
     *
     * <p>El distribuidor se obtiene del principal y nunca del cuerpo.</p>
     */
    @Operation(summary = "Registrar cisterna",
            description = "Crea una cisterna para el tenant distribuidor autenticado, identificado a partir del principal;  Requiere cuenta con ROLE_PROVIDER")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Cisterna registrada."),
            @ApiResponse(responseCode = "400", description = "El cuerpo no supera la validación o los datos de la cisterna no son válidos."),
            @ApiResponse(responseCode = "403", description = "El usuario autenticado no tiene identidad de distribuidor.")
    })
    @PostMapping
    public ResponseEntity<?> register(@Valid @RequestBody TankerInputResource resource) {
        var providerId = tenantAccess.currentProviderId();
        if (providerId.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }
        var result = fleetRegistry.registerTanker(new RegisterTankerCommand(
                providerId.get(), resource.licensePlate(), resource.brand(), resource.model(),
                resource.capacity(), resource.unit(), resource.status()));
        return ResponseEntityAssembler.toResponseEntityFromResult(
                result, TankersController::toResource, HttpStatus.CREATED);
    }

    /**
     * Lista las cisternas del distribuidor autenticado.
     *
     * <p>La consulta siempre se limita al tenant obtenido del principal.</p>
     */
    @Operation(summary = "Listar cisternas",
            description = "Devuelve las cisternas registradas para el tenant distribuidor autenticado.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Se devuelve la lista de cisternas."),
            @ApiResponse(responseCode = "403", description = "El usuario autenticado no tiene identidad de distribuidor.")
    })
    @GetMapping
    public ResponseEntity<List<TankerResource>> list() {
        var providerId = tenantAccess.currentProviderId();
        if (providerId.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }
        return new ResponseEntity<>(fleetCatalog.listTankers(providerId.get()).stream()
                .map(TankersController::toResource).toList(), HttpStatus.OK);
    }

    /**
     * Consulta una cisterna por identificador.
     *
     * <p>La consulta se limita al tenant del principal; una cisterna de otro tenant se informa como no encontrada.</p>
     */
    @Operation(summary = "Consultar cisterna por identificador",
            description = "Devuelve la cisterna indicada si pertenece al tenant distribuidor autenticado.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cisterna devuelta."),
            @ApiResponse(responseCode = "403", description = "El usuario autenticado no tiene identidad de distribuidor."),
            @ApiResponse(responseCode = "404", description = "La cisterna no existe o pertenece a otro tenant distribuidor.")
    })
    @GetMapping("/{tankerId}")
    public ResponseEntity<TankerResource> get(@PathVariable Long tankerId) {
        var providerId = tenantAccess.currentProviderId();
        if (providerId.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }
        return fleetCatalog.findTanker(tankerId)
                .filter(tanker -> providerId.get().equals(tanker.providerId()))
                .map(tanker -> new ResponseEntity<>(toResource(tanker), HttpStatus.OK))
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    /**
     * Actualiza los datos de una cisterna.
     *
     * <p>Solo el tenant propietario puede modificarla; si no existe o pertenece a otro tenant se informa como no encontrada.</p>
     */
    @Operation(summary = "Actualizar cisterna",
            description = "Aplica los cambios recibidos a una cisterna del tenant distribuidor autenticado.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cisterna actualizada."),
            @ApiResponse(responseCode = "400", description = "El cuerpo no supera la validación o los datos de la cisterna no son válidos."),
            @ApiResponse(responseCode = "404", description = "La cisterna no existe o no pertenece al tenant autenticado.")
    })
    @PutMapping("/{tankerId}")
    public ResponseEntity<?> update(@PathVariable Long tankerId,
                                    @Valid @RequestBody TankerInputResource resource) {
        if (!owns(tankerId)) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        var result = fleetRegistry.updateTanker(new UpdateTankerCommand(
                tankerId, resource.licensePlate(), resource.brand(), resource.model(),
                resource.capacity(), resource.unit(), resource.status()));
        return ResponseEntityAssembler.toResponseEntityFromResult(
                result, TankersController::toResource, HttpStatus.OK);
    }

    /**
     * Desactiva una cisterna.
     *
     * <p>Solo el tenant propietario puede desactivarla. El registro se conserva y se publica el evento correspondiente.</p>
     */
    @Operation(summary = "Desactivar cisterna",
            description = "Desactiva una cisterna del tenant autenticado sin eliminar su registro.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cisterna desactivada."),
            @ApiResponse(responseCode = "404", description = "La cisterna no existe o no pertenece al tenant autenticado.")
    })
    @PostMapping("/{tankerId}/deactivate")
    public ResponseEntity<?> deactivate(@PathVariable Long tankerId) {
        if (!owns(tankerId)) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        return ResponseEntityAssembler.toResponseEntityFromResult(
                fleetRegistry.deactivateTanker(tankerId), TankersController::toResource, HttpStatus.OK);
    }

    /**
     * Reactiva una cisterna previamente desactivada.
     *
     * <p>Solo el tenant propietario puede reactivarla; se publica el evento correspondiente.</p>
     */
    @Operation(summary = "Reactivar cisterna",
            description = "Reactiva una cisterna desactivada que pertenece al tenant distribuidor autenticado.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cisterna reactivada."),
            @ApiResponse(responseCode = "404", description = "La cisterna no existe o no pertenece al tenant autenticado.")
    })
    @PostMapping("/{tankerId}/activate")
    public ResponseEntity<?> activate(@PathVariable Long tankerId) {
        if (!owns(tankerId)) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        return ResponseEntityAssembler.toResponseEntityFromResult(
                fleetRegistry.activateTanker(tankerId), TankersController::toResource, HttpStatus.OK);
    }

    /**
     * Lista las cisternas que pueden proponerse para una entrega.
     *
     * <p>Solo incluye cisternas activas, con estado permitido y del mismo tenant; las ocupadas se excluyen.</p>
     */
    @Operation(summary = "Listar cisternas elegibles",
            description = "Devuelve cisternas activas del tenant autenticado que pueden asignarse y no están ocupadas.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Se devuelve la lista de cisternas elegibles."),
            @ApiResponse(responseCode = "403", description = "El usuario autenticado no tiene identidad de distribuidor.")
    })
    @GetMapping("/eligible")
    public ResponseEntity<List<TankerResource>> listEligible() {
        var providerId = tenantAccess.currentProviderId();
        if (providerId.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }
        return new ResponseEntity<>(eligibilityQuery.eligibleTankers(providerId.get()).stream()
                .map(TankersController::toResource).toList(), HttpStatus.OK);
    }

    /**
     * Evalúa la elegibilidad de una cisterna.
     *
     * <p>Devuelve uno de tres resultados (elegible, ocupada o no elegible) y el motivo. Se limita al tenant del principal.</p>
     */
    @Operation(summary = "Evaluar elegibilidad de cisterna",
            description = "Devuelve el resultado y motivo de elegibilidad para una cisterna del tenant autenticado.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Evaluación de elegibilidad devuelta."),
            @ApiResponse(responseCode = "403", description = "El usuario autenticado no tiene identidad de distribuidor."),
            @ApiResponse(responseCode = "404", description = "La cisterna no existe o pertenece a otro tenant distribuidor.")
    })
    @GetMapping("/{tankerId}/eligibility")
    public ResponseEntity<EligibilityResource> eligibility(@PathVariable Long tankerId) {
        var providerId = tenantAccess.currentProviderId();
        if (providerId.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }
        return eligibilityQuery.assessTanker(providerId.get(), tankerId)
                .map(assessment -> new ResponseEntity<>(
                        new EligibilityResource(assessment.outcome().name(), assessment.reason()), HttpStatus.OK))
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    private boolean owns(Long tankerId) {
        var providerId = tenantAccess.currentProviderId();
        return providerId.isPresent()
                && fleetCatalog.findTanker(tankerId)
                .map(tanker -> providerId.get().equals(tanker.providerId()))
                .orElse(false);
    }

    private static TankerResource toResource(FleetCatalog.TankerSnapshot tanker) {
        return new TankerResource(tanker.id(), tanker.providerId(), tanker.licensePlate(), tanker.brand(),
                tanker.model(), tanker.capacity(), tanker.unit(), tanker.status(), tanker.active());
    }
}
