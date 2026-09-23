package com.primefuel.fulltank.platform.tracking.interfaces.rest;

import com.primefuel.fulltank.platform.shared.interfaces.rest.resources.ErrorResource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * <strong>Reserved</strong> transport-evidence retention contract (S16/T16-B, decision U18). It defines the
 * shape and route of the delete / export operations that will manage personal transport data, but it is
 * <em>not operative</em>: both endpoints answer {@code 501 Not Implemented} with an explicit reason and never
 * touch or expose evidence.
 *
 * <p>Why reserved: the real authorization for these operations requires the platform
 * {@code ROLE_ADMIN} (T24-PRE-ADMIN), which does not exist yet. Exactly like T16-A reserved the
 * {@code ValveStateObserved} shape for T18, this ticket fixes the contract without pretending the operation
 * works. An explicit {@code 501} is deliberate — a silent {@code 404} would hide that the contract exists.
 */
@RestController
@RequestMapping(
        value = "/api/v2/admin/deliveries/{deliveryId}/transport-evidence",
        produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Transport evidence retention (reserved)",
        description = "Reserved delete/export contract for transport evidence (not operative yet)")
public class ReservedTransportEvidenceAdminController {

    static final String RESERVED_CODE = "NOT_IMPLEMENTED";
    private static final String RESERVED_MESSAGE =
            "Transport-evidence retention operations are not available yet";
    private static final String RESERVED_DETAILS =
            "Reserved contract (U18): delete/export requires the platform ROLE_ADMIN, not built yet (T24-PRE-ADMIN).";

    /**
     * Reserved: deletes a delivery's transport evidence.
     *
     * <p>Not operative until the admin role exists; always answers 501 and exposes no data.</p>
     */
    @Operation(summary = "Reserved: delete transport evidence",
            description = "Reserved contract (U18). Not operative; answers 501 until the platform admin role exists (T24-PRE-ADMIN).")
    @ApiResponses({
            @ApiResponse(responseCode = "501", description = "The operation is reserved but not implemented.")
    })
    @DeleteMapping
    public ResponseEntity<ErrorResource> delete(@PathVariable Long deliveryId) {
        return reserved();
    }

    /**
     * Reserved: exports a delivery's transport evidence.
     *
     * <p>Not operative until the admin role exists; always answers 501 and exposes no data.</p>
     */
    @Operation(summary = "Reserved: export transport evidence",
            description = "Reserved contract (U18). Not operative; answers 501 until the platform admin role exists (T24-PRE-ADMIN).")
    @ApiResponses({
            @ApiResponse(responseCode = "501", description = "The operation is reserved but not implemented.")
    })
    @GetMapping("/export")
    public ResponseEntity<ErrorResource> export(@PathVariable Long deliveryId) {
        return reserved();
    }

    private static ResponseEntity<ErrorResource> reserved() {
        return new ResponseEntity<>(new ErrorResource(RESERVED_CODE, RESERVED_MESSAGE, RESERVED_DETAILS),
                HttpStatus.NOT_IMPLEMENTED);
    }
}
