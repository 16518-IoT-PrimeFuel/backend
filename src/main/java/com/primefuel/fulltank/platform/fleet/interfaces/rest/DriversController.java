package com.primefuel.fulltank.platform.fleet.interfaces.rest;

import com.primefuel.fulltank.platform.fleet.api.EligibilityQuery;
import com.primefuel.fulltank.platform.fleet.api.FleetCatalog;
import com.primefuel.fulltank.platform.fleet.api.FleetRegistry;
import com.primefuel.fulltank.platform.fleet.domain.model.commands.RegisterDriverCommand;
import com.primefuel.fulltank.platform.fleet.domain.model.commands.UpdateDriverCommand;
import com.primefuel.fulltank.platform.fleet.interfaces.rest.resources.DriverInputResource;
import com.primefuel.fulltank.platform.fleet.interfaces.rest.resources.DriverResource;
import com.primefuel.fulltank.platform.fleet.interfaces.rest.resources.EligibilityResource;
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
 * Catálogo de conductores. El distribuidor se obtiene del principal; los recursos de otro tenant se
 * informan como no encontrados. La activación y desactivación preservan el registro.
 */
@RestController
@RequestMapping(value = "/api/drivers", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Conductores de flota", description = "Administración y ciclo de vida de conductores por distribuidor")
public class DriversController {

    private final FleetCatalog fleetCatalog;
    private final FleetRegistry fleetRegistry;
    private final EligibilityQuery eligibilityQuery;
    private final TenantAccess tenantAccess;

    public DriversController(FleetCatalog fleetCatalog,
                               FleetRegistry fleetRegistry,
                               EligibilityQuery eligibilityQuery,
                               TenantAccess tenantAccess) {
        this.fleetCatalog = fleetCatalog;
        this.fleetRegistry = fleetRegistry;
        this.eligibilityQuery = eligibilityQuery;
        this.tenantAccess = tenantAccess;
    }

    /**
     * Registra un conductor para el distribuidor autenticado.
     *
     * <p>El distribuidor se toma del principal y nunca del cuerpo, por lo que no se pueden crear conductores para otro tenant.</p>
     */
    @Operation(summary = "Registrar conductor",
            description = "Crea un conductor para el tenant distribuidor autenticado, identificado a partir del principal.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Conductor registrado."),
            @ApiResponse(responseCode = "400", description = "El cuerpo no supera la validación o los datos del conductor no son válidos."),
            @ApiResponse(responseCode = "403", description = "El usuario autenticado no tiene identidad de distribuidor.")
    })
    @PostMapping
    public ResponseEntity<?> register(@Valid @RequestBody DriverInputResource resource) {
        var providerId = tenantAccess.currentProviderId();
        if (providerId.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }
        var result = fleetRegistry.registerDriver(new RegisterDriverCommand(
                providerId.get(), resource.userId(), resource.firstName(), resource.lastName(),
                resource.licenseNumber(), resource.phoneNumber(), resource.email(), resource.status()));
        return ResponseEntityAssembler.toResponseEntityFromResult(
                result, DriversController::toResource, HttpStatus.CREATED);
    }

    /**
     * Lista los conductores del distribuidor autenticado.
     *
     * <p>La consulta siempre se limita al tenant obtenido del principal.</p>
     */
    @Operation(summary = "Listar conductores",
            description = "Devuelve los conductores registrados para el tenant distribuidor autenticado.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Se devuelve la lista de conductores."),
            @ApiResponse(responseCode = "403", description = "El usuario autenticado no tiene identidad de distribuidor.")
    })
    @GetMapping
    public ResponseEntity<List<DriverResource>> list() {
        var providerId = tenantAccess.currentProviderId();
        if (providerId.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }
        return new ResponseEntity<>(fleetCatalog.listDrivers(providerId.get()).stream()
                .map(DriversController::toResource).toList(), HttpStatus.OK);
    }

    /**
     * Consulta un conductor por identificador.
     *
     * <p>La consulta se limita al tenant del principal; un conductor de otro tenant se informa como no encontrado.</p>
     */
    @Operation(summary = "Consultar conductor por identificador",
            description = "Devuelve el conductor indicado si pertenece al tenant distribuidor autenticado.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Conductor devuelto."),
            @ApiResponse(responseCode = "403", description = "El usuario autenticado no tiene identidad de distribuidor."),
            @ApiResponse(responseCode = "404", description = "El conductor no existe o pertenece a otro tenant distribuidor.")
    })
    @GetMapping("/{driverId}")
    public ResponseEntity<DriverResource> get(@PathVariable Long driverId) {
        var providerId = tenantAccess.currentProviderId();
        if (providerId.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }
        return fleetCatalog.findDriver(driverId)
                .filter(driver -> providerId.get().equals(driver.providerId()))
                .map(driver -> new ResponseEntity<>(toResource(driver), HttpStatus.OK))
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    /**
     * Actualiza los datos de un conductor.
     *
     * <p>Solo el tenant propietario puede modificarlo; si no existe o pertenece a otro tenant se informa como no encontrado.</p>
     */
    @Operation(summary = "Actualizar conductor",
            description = "Aplica los cambios recibidos a un conductor del tenant distribuidor autenticado.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Conductor actualizado."),
            @ApiResponse(responseCode = "400", description = "El cuerpo no supera la validación o los datos del conductor no son válidos."),
            @ApiResponse(responseCode = "404", description = "El conductor no existe o no pertenece al tenant autenticado.")
    })
    @PutMapping("/{driverId}")
    public ResponseEntity<?> update(@PathVariable Long driverId,
                                    @Valid @RequestBody DriverInputResource resource) {
        if (!owns(driverId)) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        var result = fleetRegistry.updateDriver(new UpdateDriverCommand(
                driverId, resource.firstName(), resource.lastName(), resource.licenseNumber(),
                resource.phoneNumber(), resource.email(), resource.status()));
        return ResponseEntityAssembler.toResponseEntityFromResult(
                result, DriversController::toResource, HttpStatus.OK);
    }

    /**
     * Desactiva un conductor.
     *
     * <p>Solo el tenant propietario puede desactivarlo. El registro se conserva y se publica el evento correspondiente.</p>
     */
    @Operation(summary = "Desactivar conductor",
            description = "Desactiva el conductor del tenant autenticado sin eliminar su registro.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Conductor desactivado."),
            @ApiResponse(responseCode = "404", description = "El conductor no existe o no pertenece al tenant autenticado.")
    })
    @PostMapping("/{driverId}/deactivate")
    public ResponseEntity<?> deactivate(@PathVariable Long driverId) {
        if (!owns(driverId)) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        return ResponseEntityAssembler.toResponseEntityFromResult(
                fleetRegistry.deactivateDriver(driverId), DriversController::toResource, HttpStatus.OK);
    }

    /**
     * Reactiva un conductor previamente desactivado.
     *
     * <p>Solo el tenant propietario puede reactivarlo; se publica el evento correspondiente.</p>
     */
    @Operation(summary = "Reactivar conductor",
            description = "Reactiva un conductor desactivado que pertenece al tenant distribuidor autenticado.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Conductor reactivado."),
            @ApiResponse(responseCode = "404", description = "El conductor no existe o no pertenece al tenant autenticado.")
    })
    @PostMapping("/{driverId}/activate")
    public ResponseEntity<?> activate(@PathVariable Long driverId) {
        if (!owns(driverId)) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        return ResponseEntityAssembler.toResponseEntityFromResult(
                fleetRegistry.activateDriver(driverId), DriversController::toResource, HttpStatus.OK);
    }

    /**
     * Lista los conductores que pueden proponerse para una entrega.
     *
     * <p>Solo incluye conductores activos, con estado permitido y del mismo tenant; los ocupados se excluyen.</p>
     */
    @Operation(summary = "Listar conductores elegibles",
            description = "Devuelve conductores activos del tenant autenticado que pueden asignarse y no están ocupados.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Se devuelve la lista de conductores elegibles."),
            @ApiResponse(responseCode = "403", description = "El usuario autenticado no tiene identidad de distribuidor.")
    })
    @GetMapping("/eligible")
    public ResponseEntity<List<DriverResource>> listEligible() {
        var providerId = tenantAccess.currentProviderId();
        if (providerId.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }
        return new ResponseEntity<>(eligibilityQuery.eligibleDrivers(providerId.get()).stream()
                .map(DriversController::toResource).toList(), HttpStatus.OK);
    }

    /**
     * Evalúa la elegibilidad de un conductor.
     *
     * <p>Devuelve uno de tres resultados (elegible, ocupado o no elegible) y el motivo. Se limita al tenant del principal.</p>
     */
    @Operation(summary = "Evaluar elegibilidad del conductor",
            description = "Devuelve el resultado y motivo de elegibilidad para un conductor del tenant autenticado.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Evaluación de elegibilidad devuelta."),
            @ApiResponse(responseCode = "403", description = "El usuario autenticado no tiene identidad de distribuidor."),
            @ApiResponse(responseCode = "404", description = "El conductor no existe o pertenece a otro tenant distribuidor.")
    })
    @GetMapping("/{driverId}/eligibility")
    public ResponseEntity<EligibilityResource> eligibility(@PathVariable Long driverId) {
        var providerId = tenantAccess.currentProviderId();
        if (providerId.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }
        return eligibilityQuery.assessDriver(providerId.get(), driverId)
                .map(assessment -> new ResponseEntity<>(
                        new EligibilityResource(assessment.outcome().name(), assessment.reason()), HttpStatus.OK))
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    private boolean owns(Long driverId) {
        var providerId = tenantAccess.currentProviderId();
        return providerId.isPresent()
                && fleetCatalog.findDriver(driverId)
                .map(driver -> providerId.get().equals(driver.providerId()))
                .orElse(false);
    }

    private static DriverResource toResource(FleetCatalog.DriverSnapshot driver) {
        return new DriverResource(driver.id(), driver.providerId(), driver.userId(), driver.firstName(),
                driver.lastName(), driver.licenseNumber(), driver.phoneNumber(), driver.email(),
                driver.status(), driver.active());
    }
}
