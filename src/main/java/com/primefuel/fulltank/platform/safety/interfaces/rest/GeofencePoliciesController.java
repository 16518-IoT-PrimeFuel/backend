package com.primefuel.fulltank.platform.safety.interfaces.rest;

import com.primefuel.fulltank.platform.fulfillment.api.DeliveryTrackingLookup;
import com.primefuel.fulltank.platform.iam.api.TenantAccess;
import com.primefuel.fulltank.platform.safety.api.GeofencePolicies;
import com.primefuel.fulltank.platform.safety.domain.model.commands.CreateGeofencePolicyCommand;
import com.primefuel.fulltank.platform.safety.interfaces.rest.resources.CreateGeofencePolicyResource;
import com.primefuel.fulltank.platform.safety.interfaces.rest.resources.GeofencePolicyResource;
import com.primefuel.fulltank.platform.shared.interfaces.rest.transform.ResponseEntityAssembler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Administración v2 de políticas de geocerca. Crea una nueva versión del círculo usado para evaluar
 * decisiones de seguridad de una entrega, sin modificar versiones anteriores.
 *
 * <p>La geocerca es un dato para la decisión de seguridad, no un mecanismo de prevención física. La autorización
 * o bloqueo lógico de una válvula se resuelve por separado.
 *
 * <p>Solo el distribuidor propietario puede configurarla. El tenant se obtiene del principal y la entrega, nunca
 * del cuerpo; una entrega ajena devuelve 403 y una inexistente, 404.
 */
@RestController
@RequestMapping(value = "/api/v2/deliveries", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Políticas de geocerca", description = "Configuración versionada del área de seguridad de una entrega")
public class GeofencePoliciesController {

    private final GeofencePolicies geofencePolicies;
    private final DeliveryTrackingLookup deliveryTrackingLookup;
    private final TenantAccess tenantAccess;

    public GeofencePoliciesController(GeofencePolicies geofencePolicies,
                                      DeliveryTrackingLookup deliveryTrackingLookup,
                                      TenantAccess tenantAccess) {
        this.geofencePolicies = geofencePolicies;
        this.deliveryTrackingLookup = deliveryTrackingLookup;
        this.tenantAccess = tenantAccess;
    }

    /**
     * Crea una versión nueva de la política de geocerca de una entrega.
     *
     * <p>El distribuidor se resuelve en el servidor. El radio define un círculo en metros; la geometría inválida devuelve 400.
     */
    @Operation(summary = "Crear versión de geocerca para entrega",
            description = "Crea una nueva versión del círculo de seguridad de una entrega para el distribuidor autenticado y conserva las versiones previas.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Versión de política creada."),
            @ApiResponse(responseCode = "400", description = "Geometría inválida: falta el centro, sus coordenadas no son válidas o el radio no es positivo."),
            @ApiResponse(responseCode = "403", description = "El usuario no tiene identidad de distribuidor o no es propietario de la entrega."),
            @ApiResponse(responseCode = "404", description = "La entrega no existe."),
            @ApiResponse(responseCode = "409", description = "Ya existe una versión concurrente de la política para esta entrega.")
    })
    @PostMapping("/{deliveryId}/geofence-policies")
    public ResponseEntity<?> create(@PathVariable Long deliveryId,
                                    @Valid @RequestBody CreateGeofencePolicyResource resource) {
        var providerId = tenantAccess.currentProviderId();
        if (providerId.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }
        var delivery = deliveryTrackingLookup.findAssignedDelivery(deliveryId);
        if (delivery.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        if (!providerId.get().equals(delivery.get().providerId())) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }
        var result = geofencePolicies.createPolicy(new CreateGeofencePolicyCommand(
                deliveryId, providerId.get(), resource.centerLatitude(), resource.centerLongitude(),
                resource.radiusMeters()));
        return ResponseEntityAssembler.toResponseEntityFromResult(
                result, GeofencePoliciesController::toResource, HttpStatus.CREATED);
    }

    private static GeofencePolicyResource toResource(GeofencePolicies.PolicySnapshot policy) {
        return new GeofencePolicyResource(policy.id(), policy.deliveryId(), policy.providerId(),
                policy.centerLatitude(), policy.centerLongitude(), policy.radiusMeters(),
                policy.policyVersion());
    }
}
