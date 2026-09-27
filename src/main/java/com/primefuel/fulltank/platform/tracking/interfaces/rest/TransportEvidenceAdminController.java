package com.primefuel.fulltank.platform.tracking.interfaces.rest;

import com.primefuel.fulltank.platform.fulfillment.api.DeliveryTrackingLookup;
import com.primefuel.fulltank.platform.tracking.api.DeliveryTrackingQuery;
import com.primefuel.fulltank.platform.tracking.domain.repositories.DeliveryTrackingRepository;
import com.primefuel.fulltank.platform.tracking.domain.repositories.TransportEvidenceSampleRepository;
import com.primefuel.fulltank.platform.tracking.interfaces.rest.resources.TrackingSampleResource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping(value = "/api/v2/admin/deliveries/{deliveryId}/transport-evidence",
        produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Retención de evidencias de transporte", description = "Exportación y eliminación administrativa de evidencias GPS")
public class TransportEvidenceAdminController {
    private final DeliveryTrackingLookup deliveryLookup;
    private final DeliveryTrackingQuery trackingQuery;
    private final TransportEvidenceSampleRepository samples;
    private final DeliveryTrackingRepository tracking;

    public TransportEvidenceAdminController(DeliveryTrackingLookup deliveryLookup,
                                            DeliveryTrackingQuery trackingQuery,
                                            TransportEvidenceSampleRepository samples,
                                            DeliveryTrackingRepository tracking) {
        this.deliveryLookup = deliveryLookup;
        this.trackingQuery = trackingQuery;
        this.samples = samples;
        this.tracking = tracking;
    }

    /** Elimina muestras GPS y su proyección reconstruible; conserva transiciones de negocio y decisiones de seguridad. */
    @DeleteMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @Transactional
    @Operation(summary = "Eliminar evidencias de transporte",
            description = "Elimina únicamente muestras GPS y la proyección de seguimiento de la entrega. Requiere ROLE_ADMIN.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Evidencias eliminadas; repetir la operación no produce cambios adicionales."),
            @ApiResponse(responseCode = "403", description = "El usuario no tiene autoridad de administrador de plataforma."),
            @ApiResponse(responseCode = "404", description = "La entrega no existe.")
    })
    public ResponseEntity<Void> delete(@PathVariable Long deliveryId) {
        if (deliveryLookup.findAssignedDelivery(deliveryId).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        samples.deleteByDeliveryId(deliveryId);
        tracking.deleteByDeliveryId(deliveryId);
        return ResponseEntity.noContent().build();
    }

    /** Exporta evidencias con los mismos campos y orden que la consulta de muestras de seguimiento. */
    @GetMapping("/export")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @Operation(summary = "Exportar evidencias de transporte",
            description = "Devuelve evidencias GPS y de carga ordenadas por instante de registro. Requiere ROLE_ADMIN.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Evidencias exportadas; la lista puede estar vacía."),
            @ApiResponse(responseCode = "403", description = "El usuario no tiene autoridad de administrador de plataforma."),
            @ApiResponse(responseCode = "404", description = "La entrega no existe.")
    })
    public ResponseEntity<?> export(@PathVariable Long deliveryId) {
        if (deliveryLookup.findAssignedDelivery(deliveryId).isEmpty()) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        var resources = trackingQuery.samples(deliveryId).stream()
                .map(snapshot -> new TrackingSampleResource(snapshot.evidenceId(), snapshot.kind(),
                        snapshot.latitude(), snapshot.longitude(), snapshot.accuracyMeters(),
                        snapshot.milestone(), snapshot.volume(), snapshot.unit(), snapshot.recordedAt(),
                        snapshot.receivedAt(), snapshot.latestAdvanced()))
                .toList();
        return ResponseEntity.ok(new ExportResource(deliveryId, resources));
    }

    public record ExportResource(Long deliveryId, List<TrackingSampleResource> samples) { }
}
