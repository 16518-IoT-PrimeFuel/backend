package com.primefuel.fulltank.platform.tracking.interfaces.rest;

import com.primefuel.fulltank.platform.fleet.api.FleetCatalog;
import com.primefuel.fulltank.platform.fulfillment.api.DeliveryTrackingLookup;
import com.primefuel.fulltank.platform.iam.api.MembershipAccess;
import com.primefuel.fulltank.platform.iam.api.TenantAccess;
import com.primefuel.fulltank.platform.tracking.api.DeliveryTrackingQuery;
import com.primefuel.fulltank.platform.tracking.interfaces.rest.resources.DeliveryTrackingResource;
import com.primefuel.fulltank.platform.tracking.interfaces.rest.resources.TrackingSampleResource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Consulta v2 del seguimiento de transporte. Expone la última proyección confiable de la entrega y su
 * historial cronológico mediante {@link DeliveryTrackingQuery}.
 *
 * <p>El acceso se resuelve en el servidor con la asignación y el principal: pueden consultar el distribuidor
 * propietario y el conductor asignado. Otro tenant recibe 403; una entrega inexistente recibe 404.
 */
@RestController
@RequestMapping(value = "/api/v2/deliveries", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Seguimiento de transporte", description = "Consulta de ubicación y evidencias de transporte de entregas")
public class DeliveryTrackingQueryController {

    private final DeliveryTrackingQuery trackingQuery;
    private final DeliveryTrackingLookup deliveryTrackingLookup;
    private final FleetCatalog fleetCatalog;
    private final TenantAccess tenantAccess;
    private final MembershipAccess membershipAccess;

    public DeliveryTrackingQueryController(DeliveryTrackingQuery trackingQuery,
                                           DeliveryTrackingLookup deliveryTrackingLookup,
                                           FleetCatalog fleetCatalog,
                                           TenantAccess tenantAccess,
                                           MembershipAccess membershipAccess) {
        this.trackingQuery = trackingQuery;
        this.deliveryTrackingLookup = deliveryTrackingLookup;
        this.fleetCatalog = fleetCatalog;
        this.tenantAccess = tenantAccess;
        this.membershipAccess = membershipAccess;
    }

    /**
     * Consulta el último seguimiento confiable de una entrega.
     *
     * <p>Visible para el distribuidor propietario o el conductor asignado. Se rechaza otro tenant y se informa como no encontrada una entrega sin seguimiento.</p>
     */
    @Operation(summary = "Consultar último seguimiento de entrega",
            description = "Devuelve la última ubicación confiable y el estado de carga para el distribuidor propietario o el conductor asignado.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Último seguimiento devuelto."),
            @ApiResponse(responseCode = "403", description = "El usuario no pertenece al distribuidor propietario ni es el conductor asignado."),
            @ApiResponse(responseCode = "404", description = "La entrega no existe o todavía no tiene seguimiento.")
    })
    @GetMapping("/{deliveryId}/tracking")
    public ResponseEntity<?> latest(@PathVariable Long deliveryId) {
        var denial = denialOrNull(deliveryId);
        if (denial != null) {
            return denial;
        }
        return trackingQuery.latest(deliveryId)
                .map(snapshot -> new ResponseEntity<>(toResource(snapshot), HttpStatus.OK))
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    /**
     * Consulta el historial cronológico de seguimiento según la hora del dispositivo.
     *
     * <p>Cada muestra conserva el instante de recepción y si actualizó el último dato, incluso ante muestras desordenadas. Visible para el distribuidor propietario o el conductor asignado.</p>
     */
    @Operation(summary = "Listar muestras de seguimiento de entrega",
            description = "Devuelve las evidencias de transporte ordenadas por hora del dispositivo, incluyendo la recepción y si cada muestra actualizó el dato más reciente.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Historial devuelto; puede estar vacío."),
            @ApiResponse(responseCode = "403", description = "El usuario no pertenece al distribuidor propietario ni es el conductor asignado."),
            @ApiResponse(responseCode = "404", description = "La entrega no existe.")
    })
    @GetMapping("/{deliveryId}/tracking/samples")
    public ResponseEntity<?> samples(@PathVariable Long deliveryId) {
        var denial = denialOrNull(deliveryId);
        if (denial != null) {
            return denial;
        }
        var resources = trackingQuery.samples(deliveryId).stream()
                .map(snapshot -> new TrackingSampleResource(snapshot.evidenceId(), snapshot.kind(),
                        snapshot.latitude(), snapshot.longitude(), snapshot.accuracyMeters(),
                        snapshot.milestone(), snapshot.volume(), snapshot.unit(), snapshot.recordedAt(),
                        snapshot.receivedAt(), snapshot.latestAdvanced()))
                .toList();
        return new ResponseEntity<>(resources, HttpStatus.OK);
    }

    /**
     * Determina si el usuario puede consultar el seguimiento. Devuelve {@code null} cuando está autorizado o la
     * respuesta de denegación correspondiente. Puede acceder el distribuidor propietario o el conductor asignado
     * en una asignación consistente entre tenants; si faltan la entrega o el conductor, se informa como no encontrado.
     */
    private ResponseEntity<?> denialOrNull(Long deliveryId) {
        var delivery = deliveryTrackingLookup.findAssignedDelivery(deliveryId);
        if (delivery.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        var assignment = delivery.get();
        var driver = fleetCatalog.findDriver(assignment.driverId());
        if (driver.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        var assignedDriver = driver.get();

        boolean providerOwner = tenantAccess.currentProviderId()
                .map(providerId -> providerId.equals(assignment.providerId()))
                .orElse(false);
        boolean tenantConsistent = assignment.providerId().equals(assignedDriver.providerId());
        boolean assignedDriverCaller = tenantConsistent
                && assignedDriver.userId() != null
                && membershipAccess.currentUserId()
                .map(userId -> userId.equals(assignedDriver.userId()))
                .orElse(false);

        if (providerOwner || assignedDriverCaller) {
            return null;
        }
        return new ResponseEntity<>(HttpStatus.FORBIDDEN);
    }

    private static DeliveryTrackingResource toResource(DeliveryTrackingQuery.TrackingSnapshot snapshot) {
        return new DeliveryTrackingResource(snapshot.deliveryId(), snapshot.providerId(), snapshot.driverId(),
                snapshot.lastLatitude(), snapshot.lastLongitude(), snapshot.lastAccuracyMeters(),
                snapshot.lastPositionAt(), snapshot.lastPositionEvidenceId(), snapshot.loaded(),
                snapshot.lastLoadMilestone(), snapshot.lastLoadAt(), snapshot.lastLoadVolume(),
                snapshot.lastLoadUnit(), snapshot.lastLoadEvidenceId(), snapshot.version());
    }
}
