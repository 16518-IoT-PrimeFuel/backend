package com.primefuel.fulltank.platform.tracking.interfaces.rest;

import com.primefuel.fulltank.platform.fleet.api.FleetCatalog;
import com.primefuel.fulltank.platform.fulfillment.api.DeliveryTrackingLookup;
import com.primefuel.fulltank.platform.iam.api.MembershipAccess;
import com.primefuel.fulltank.platform.shared.application.result.ApplicationError;
import com.primefuel.fulltank.platform.shared.application.result.Result;
import com.primefuel.fulltank.platform.shared.interfaces.rest.transform.ErrorResponseAssembler;
import com.primefuel.fulltank.platform.shared.interfaces.rest.transform.ResponseEntityAssembler;
import com.primefuel.fulltank.platform.tracking.api.TransportEvidenceRecorder;
import com.primefuel.fulltank.platform.tracking.domain.model.commands.RecordLoadEvidenceCommand;
import com.primefuel.fulltank.platform.tracking.domain.model.commands.RecordPositionEvidenceCommand;
import com.primefuel.fulltank.platform.tracking.domain.model.valueobjects.LoadMilestone;
import com.primefuel.fulltank.platform.tracking.domain.model.valueobjects.TransportEvidenceKind;
import com.primefuel.fulltank.platform.tracking.interfaces.rest.resources.TransportEvidenceAckResource;
import com.primefuel.fulltank.platform.tracking.interfaces.rest.resources.TransportEvidenceResource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Evidencia de transporte informada desde la aplicación del conductor mediante una solicitud autenticada.
 *
 * <p>Solo puede informar el conductor asignado, resuelto desde la asignación y el catálogo de flota, nunca desde
 * el cuerpo. Una entrega inexistente devuelve 404; una asignación inconsistente o conductor distinto, 403;
 * un cuerpo inválido, 400; y una secuencia de carga imposible, 422.
 *
 * <p>Una ubicación tardía se conserva como evidencia, pero no reemplaza el último valor de la proyección; la respuesta lo indica en {@code latestAdvanced}.</p>
 */
@RestController
@RequestMapping(value = "/api/deliveries", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Evidencias de transporte", description = "Ubicaciones y eventos de carga informados por el conductor")
public class TransportEvidenceController {

    private final TransportEvidenceRecorder transportEvidenceRecorder;
    private final DeliveryTrackingLookup deliveryTrackingLookup;
    private final FleetCatalog fleetCatalog;
    private final MembershipAccess membershipAccess;

    public TransportEvidenceController(TransportEvidenceRecorder transportEvidenceRecorder,
                                       DeliveryTrackingLookup deliveryTrackingLookup,
                                       FleetCatalog fleetCatalog,
                                       MembershipAccess membershipAccess) {
        this.transportEvidenceRecorder = transportEvidenceRecorder;
        this.deliveryTrackingLookup = deliveryTrackingLookup;
        this.fleetCatalog = fleetCatalog;
        this.membershipAccess = membershipAccess;
    }

    /**
     * Registra una evidencia de transporte para una entrega.
     *
     * <p>Solo el conductor asignado puede informar evidencias. La asignación se resuelve en el servidor y una ubicación tardía se conserva sin retroceder la proyección.</p>
     */
    @Operation(summary = "Registrar evidencia de transporte",
            description = "Acepta una ubicación o hito de carga enviado por el conductor asignado; conserva las muestras tardías sin reemplazar el último valor.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "El eventId ya se registró para la entrega; se devuelve la confirmación original sin guardar otra muestra."),
            @ApiResponse(responseCode = "201", description = "Evidencia registrada; una muestra tardía puede no actualizar el último valor."),
            @ApiResponse(responseCode = "400", description = "El cuerpo no es válido: tipo o hito desconocido, coordenadas ausentes o volumen no positivo."),
            @ApiResponse(responseCode = "403", description = "El usuario no es el conductor asignado o la asignación cruza tenants."),
            @ApiResponse(responseCode = "404", description = "La entrega o el conductor asignado no existe."),
            @ApiResponse(responseCode = "422", description = "El hito de carga no es válido para el estado actual, por ejemplo, descargar antes de cargar.")
    })
    @PostMapping("/{deliveryId}/transport-evidence")
    public ResponseEntity<?> record(@PathVariable Long deliveryId,
                                    @Valid @RequestBody TransportEvidenceResource resource) {
        var userId = membershipAccess.currentUserId();
        if (userId.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }

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

        // S16 invariant: driver and delivery share a tenant. A mismatch is an inconsistent assignment.
        if (!assignment.providerId().equals(assignedDriver.providerId())) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }
        // The caller must be the assigned driver (identity resolved from the principal, never the body).
        if (assignedDriver.userId() == null || !assignedDriver.userId().equals(userId.get())) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }

        TransportEvidenceKind kind;
        try {
            kind = TransportEvidenceKind.fromCode(resource.type());
        } catch (IllegalArgumentException exception) {
            return error(ApplicationError.validationError("type", exception.getMessage()));
        }

        try {
            return switch (kind) {
                case POSITION -> respond(transportEvidenceRecorder.recordPosition(new RecordPositionEvidenceCommand(
                        assignment.deliveryId(), assignment.providerId(), assignment.driverId(),
                        resource.latitude(), resource.longitude(), resource.accuracyMeters(),
                        resource.recordedAt(), resource.eventId())));
                case LOAD -> recordLoad(assignment, resource);
            };
        } catch (DataIntegrityViolationException exception) {
            return transportEvidenceRecorder.findReplay(assignment.deliveryId(), resource.eventId())
                    .map(ack -> respond(Result.success(ack)))
                    .orElseThrow(() -> exception);
        }
    }

    private ResponseEntity<?> recordLoad(DeliveryTrackingLookup.AssignedDeliverySnapshot assignment,
                                         TransportEvidenceResource resource) {
        LoadMilestone milestone;
        try {
            milestone = LoadMilestone.fromCode(resource.milestone());
        } catch (IllegalArgumentException exception) {
            return error(ApplicationError.validationError("milestone", exception.getMessage()));
        }
        return respond(transportEvidenceRecorder.recordLoad(new RecordLoadEvidenceCommand(
                assignment.deliveryId(), assignment.providerId(), assignment.driverId(), milestone,
                resource.volume(), resource.unit(), resource.recordedAt(), resource.eventId())));
    }

    /** 201 for a new sample, 200 when the {@code eventId} replays one already stored. */
    private static ResponseEntity<?> respond(Result<TransportEvidenceRecorder.EvidenceAck, ApplicationError> result) {
        var replayed = result instanceof Result.Success<TransportEvidenceRecorder.EvidenceAck, ApplicationError> s
                && s.value().replayed();
        return ResponseEntityAssembler.toResponseEntityFromResult(result, TransportEvidenceController::toResource,
                replayed ? HttpStatus.OK : HttpStatus.CREATED);
    }

    private static ResponseEntity<?> error(ApplicationError applicationError) {
        return ErrorResponseAssembler.toErrorResponseFromApplicationError(applicationError);
    }

    private static TransportEvidenceAckResource toResource(TransportEvidenceRecorder.EvidenceAck ack) {
        return new TransportEvidenceAckResource(ack.evidenceId(), ack.deliveryId(), ack.kind(), ack.milestone(),
                ack.latestAdvanced(), ack.recordedAt());
    }
}
