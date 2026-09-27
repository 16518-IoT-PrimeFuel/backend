package com.primefuel.fulltank.platform.replenishment.interfaces.rest;

import com.primefuel.fulltank.platform.iam.api.MembershipAccess;
import com.primefuel.fulltank.platform.iam.api.TenantAccess;
import com.primefuel.fulltank.platform.replenishment.application.commandservices.ReplenishmentCommandService;
import com.primefuel.fulltank.platform.replenishment.application.queryservices.ReplenishmentQueryService;
import com.primefuel.fulltank.platform.replenishment.domain.model.commands.AcceptReplenishmentRequestCommand;
import com.primefuel.fulltank.platform.replenishment.domain.model.commands.CancelReplenishmentRequestCommand;
import com.primefuel.fulltank.platform.replenishment.domain.model.commands.CreateReplenishmentRequestCommand;
import com.primefuel.fulltank.platform.replenishment.domain.model.commands.RejectReplenishmentRequestCommand;
import com.primefuel.fulltank.platform.replenishment.domain.model.queries.GetReplenishmentRequestByIdQuery;
import com.primefuel.fulltank.platform.replenishment.domain.model.queries.GetReplenishmentRequestsByOrganizationQuery;
import com.primefuel.fulltank.platform.replenishment.domain.model.valueobjects.ReplenishmentSource;
import com.primefuel.fulltank.platform.replenishment.interfaces.rest.resources.CreateReplenishmentRequestResource;
import com.primefuel.fulltank.platform.replenishment.interfaces.rest.resources.RejectReplenishmentRequestResource;
import com.primefuel.fulltank.platform.replenishment.interfaces.rest.resources.ReplenishmentRequestResource;
import com.primefuel.fulltank.platform.replenishment.interfaces.rest.transform.ReplenishmentRequestResourceFromDomainAssembler;
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

@RestController
@RequestMapping(value = "/api/v2/replenishment-requests", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Solicitudes de abastecimiento", description = "Creación y ciclo de decisión de solicitudes por organización")
public class ReplenishmentRequestsController {

    private final ReplenishmentCommandService commandService;
    private final ReplenishmentQueryService queryService;
    private final MembershipAccess membershipAccess;
    private final TenantAccess tenantAccess;

    public ReplenishmentRequestsController(ReplenishmentCommandService commandService,
                                           ReplenishmentQueryService queryService,
                                           MembershipAccess membershipAccess,
                                           TenantAccess tenantAccess) {
        this.commandService = commandService;
        this.queryService = queryService;
        this.membershipAccess = membershipAccess;
        this.tenantAccess = tenantAccess;
    }

    /**
     * Crea una solicitud de abastecimiento para la organización activa.
     *
     * <p>La organización se deriva de la membresía autenticada. El producto debe estar disponible para el distribuidor indicado; {@code episodeKey} evita duplicados de episodios automáticos.</p>
     */
    @Operation(summary = "Crear solicitud de abastecimiento",
            description = "Registra una solicitud para la organización activa. Cuando se envía {@code episodeKey}, una repetición devuelve la solicitud ya creada para ese episodio.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Solicitud creada o solicitud existente del episodio devuelta."),
            @ApiResponse(responseCode = "400", description = "El cuerpo es inválido, no se resolvió una organización o el valor de un campo no está permitido."),
            @ApiResponse(responseCode = "403", description = "El usuario no está autenticado o no tiene una organización activa."),
            @ApiResponse(responseCode = "404", description = "El producto no está disponible para el distribuidor indicado.")
    })
    @PostMapping
    public ResponseEntity<?> create(@Valid @RequestBody CreateReplenishmentRequestResource resource) {
        var organizationId = membershipAccess.currentOrganizationId();
        if (organizationId.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }
        var source = resource.source() == null
                ? ReplenishmentSource.MANUAL
                : ReplenishmentSource.valueOf(resource.source().trim().toUpperCase());
        var result = commandService.handle(new CreateReplenishmentRequestCommand(
                organizationId.get(), resource.customerAccountId(), resource.tankId(), resource.providerId(),
                resource.fuelProductId(), resource.quantity(), resource.unit(), source, resource.episodeKey()));
        return ResponseEntityAssembler.toResponseEntityFromResult(
                result, ReplenishmentRequestResourceFromDomainAssembler::toResourceFromDomain, HttpStatus.CREATED);
    }

    /**
     * Lista las solicitudes de abastecimiento de la organización activa.
     *
     * <p>La organización se obtiene de la identidad autenticada y no se acepta como parámetro.</p>
     */
    @Operation(summary = "Listar solicitudes de abastecimiento",
            description = "Devuelve las solicitudes que pertenecen a la organización activa del usuario.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Solicitudes de abastecimiento devueltas."),
            @ApiResponse(responseCode = "403", description = "El usuario no está autenticado o no tiene una organización activa.")
    })
    @GetMapping
    public ResponseEntity<List<ReplenishmentRequestResource>> list() {
        var organizationId = membershipAccess.currentOrganizationId();
        if (organizationId.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }
        var requests = queryService.handle(new GetReplenishmentRequestsByOrganizationQuery(organizationId.get()));
        return new ResponseEntity<>(requests.stream()
                .map(ReplenishmentRequestResourceFromDomainAssembler::toResourceFromDomain).toList(), HttpStatus.OK);
    }

    /**
     * Consulta una solicitud de abastecimiento por identificador.
     *
     * <p>La solicitud debe pertenecer a la organización activa; las de otros tenants responden como no encontradas.</p>
     */
    @Operation(summary = "Consultar solicitud por identificador",
            description = "Devuelve la solicitud indicada solo si pertenece a la organización activa del usuario.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Solicitud de abastecimiento devuelta."),
            @ApiResponse(responseCode = "403", description = "El usuario no está autenticado o no tiene una organización activa."),
            @ApiResponse(responseCode = "404", description = "La solicitud no existe o pertenece a otra organización.")
    })
    @GetMapping("/{requestId}")
    public ResponseEntity<ReplenishmentRequestResource> get(@PathVariable Long requestId) {
        var organizationId = membershipAccess.currentOrganizationId();
        if (organizationId.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }
        return queryService.handle(new GetReplenishmentRequestByIdQuery(requestId))
                .filter(request -> organizationId.get().equals(request.getOrganizationId()))
                .map(request -> new ResponseEntity<>(
                        ReplenishmentRequestResourceFromDomainAssembler.toResourceFromDomain(request), HttpStatus.OK))
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    /**
     * Acepta una solicitud de abastecimiento pendiente.
     *
     * <p>Solo el distribuidor destinatario puede aceptarla. La decisión requiere que siga pendiente; una decisión previa o concurrente produce conflicto.</p>
     */
    @Operation(summary = "Aceptar solicitud de abastecimiento",
            description = "Registra la aceptación para el distribuidor destinatario mientras la solicitud siga pendiente.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Solicitud de abastecimiento aceptada."),
            @ApiResponse(responseCode = "400", description = "El estado o los argumentos no permiten aceptar la solicitud."),
            @ApiResponse(responseCode = "403", description = "El usuario no representa al distribuidor destinatario."),
            @ApiResponse(responseCode = "404", description = "No existe la solicitud indicada."),
            @ApiResponse(responseCode = "409", description = "La solicitud ya no está pendiente o se decidió en paralelo.")
    })
    @PostMapping("/{requestId}/accept")
    public ResponseEntity<?> accept(@PathVariable Long requestId) {
        if (!providerOwns(requestId)) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }
        var result = commandService.handle(new AcceptReplenishmentRequestCommand(requestId));
        return ResponseEntityAssembler.toResponseEntityFromResult(
                result, ReplenishmentRequestResourceFromDomainAssembler::toResourceFromDomain, HttpStatus.OK);
    }

    /**
     * Rechaza una solicitud de abastecimiento pendiente.
     *
     * <p>Solo el distribuidor destinatario puede rechazarla. Debe incluirse el motivo y la solicitud debe seguir pendiente.</p>
     */
    @Operation(summary = "Rechazar solicitud de abastecimiento",
            description = "Registra el rechazo y su motivo para el distribuidor destinatario, siempre que la solicitud siga pendiente.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Solicitud de abastecimiento rechazada."),
            @ApiResponse(responseCode = "400", description = "El cuerpo es inválido o no se pudo rechazar la solicitud."),
            @ApiResponse(responseCode = "403", description = "El usuario no representa al distribuidor destinatario."),
            @ApiResponse(responseCode = "404", description = "No existe la solicitud indicada."),
            @ApiResponse(responseCode = "409", description = "La solicitud ya no está pendiente o se decidió en paralelo.")
    })
    @PostMapping("/{requestId}/reject")
    public ResponseEntity<?> reject(@PathVariable Long requestId,
                                    @Valid @RequestBody RejectReplenishmentRequestResource resource) {
        if (!providerOwns(requestId)) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }
        var result = commandService.handle(new RejectReplenishmentRequestCommand(requestId, resource.reason()));
        return ResponseEntityAssembler.toResponseEntityFromResult(
                result, ReplenishmentRequestResourceFromDomainAssembler::toResourceFromDomain, HttpStatus.OK);
    }

    /**
     * Cancela una solicitud de abastecimiento.
     *
     * <p>Solo la organización propietaria puede cancelarla mientras siga pendiente.</p>
     */
    @Operation(summary = "Cancelar solicitud de abastecimiento",
            description = "Registra la cancelación por la organización propietaria; la solicitud debe permanecer pendiente.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Solicitud de abastecimiento cancelada."),
            @ApiResponse(responseCode = "403", description = "El usuario no tiene organización activa o no es propietario de la solicitud."),
            @ApiResponse(responseCode = "404", description = "No existe la solicitud indicada."),
            @ApiResponse(responseCode = "409", description = "La solicitud ya no está pendiente o se decidió en paralelo.")
    })
    @PostMapping("/{requestId}/cancel")
    public ResponseEntity<?> cancel(@PathVariable Long requestId) {
        var organizationId = membershipAccess.currentOrganizationId();
        if (organizationId.isEmpty() || !organizationIdOwns(requestId, organizationId.get())) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }
        var result = commandService.handle(new CancelReplenishmentRequestCommand(requestId));
        return ResponseEntityAssembler.toResponseEntityFromResult(
                result, ReplenishmentRequestResourceFromDomainAssembler::toResourceFromDomain, HttpStatus.OK);
    }

    private boolean providerOwns(Long requestId) {
        var providerId = tenantAccess.currentProviderId();
        return providerId.isPresent()
                && queryService.handle(new GetReplenishmentRequestByIdQuery(requestId))
                .map(request -> providerId.get().equals(request.getProviderId()))
                .orElse(false);
    }

    private boolean organizationIdOwns(Long requestId, Long organizationId) {
        return queryService.handle(new GetReplenishmentRequestByIdQuery(requestId))
                .map(request -> organizationId.equals(request.getOrganizationId()))
                .orElse(false);
    }
}
