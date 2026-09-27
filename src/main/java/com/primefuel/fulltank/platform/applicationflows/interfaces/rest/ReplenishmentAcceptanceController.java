package com.primefuel.fulltank.platform.applicationflows.interfaces.rest;

import com.primefuel.fulltank.platform.applicationflows.ReplenishmentAcceptanceFlow;
import com.primefuel.fulltank.platform.iam.api.TenantAccess;
import com.primefuel.fulltank.platform.replenishment.api.ReplenishmentLookup;
import com.primefuel.fulltank.platform.replenishment.interfaces.rest.transform.ReplenishmentRequestResourceFromDomainAssembler;
import com.primefuel.fulltank.platform.shared.interfaces.rest.transform.ResponseEntityAssembler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Endpoint de composición para aceptar una solicitud y crear y vincular su orden en la misma transacción. */
@RestController
@RequestMapping(value = "/api/v2/replenishment-requests", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Solicitudes de abastecimiento", description = "Creación y ciclo de decisión de solicitudes por organización")
public class ReplenishmentAcceptanceController {

    private final ReplenishmentAcceptanceFlow acceptanceFlow;
    private final TenantAccess tenantAccess;
    private final ReplenishmentLookup requests;

    public ReplenishmentAcceptanceController(ReplenishmentAcceptanceFlow acceptanceFlow,
                                             TenantAccess tenantAccess,
                                             ReplenishmentLookup requests) {
        this.acceptanceFlow = acceptanceFlow;
        this.tenantAccess = tenantAccess;
        this.requests = requests;
    }

    /** Acepta la solicitud del distribuidor autenticado, crea y vincula la orden y devuelve su identificador. */
    @Operation(summary = "Aceptar solicitud de abastecimiento",
            description = "El distribuidor destinatario acepta una solicitud pendiente. La transacción consume la decisión, crea y vincula una orden para permitir su asignación posterior.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Solicitud aceptada y orden creada y vinculada; la respuesta incluye orderId."),
            @ApiResponse(responseCode = "403", description = "El usuario no representa al distribuidor destinatario."),
            @ApiResponse(responseCode = "404", description = "No existe la solicitud indicada."),
            @ApiResponse(responseCode = "409", description = "La solicitud no puede aceptarse, falta un mapeo heredado o no tiene dirección y fecha de entrega; no se conservan cambios parciales.")
    })
    @PostMapping("/{requestId}/accept")
    public ResponseEntity<?> accept(@PathVariable Long requestId) {
        var providerId = tenantAccess.currentProviderId();
        if (providerId.isEmpty() || requests.findById(requestId)
                .filter(request -> providerId.get().equals(request.providerId())).isEmpty()) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }
        var result = acceptanceFlow.accept(requestId);
        return ResponseEntityAssembler.toResponseEntityFromResult(
                result, ReplenishmentRequestResourceFromDomainAssembler::toResourceFromDomain, HttpStatus.OK);
    }
}
