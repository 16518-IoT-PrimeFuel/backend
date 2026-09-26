package com.primefuel.fulltank.platform.telemetry.interfaces.rest;

import com.primefuel.fulltank.platform.shared.interfaces.rest.transform.ResponseEntityAssembler;
import com.primefuel.fulltank.platform.telemetry.application.internal.commandservices.TelemetryIngestServiceImpl;
import com.primefuel.fulltank.platform.telemetry.domain.model.commands.IngestTelemetryCommand;
import com.primefuel.fulltank.platform.telemetry.interfaces.rest.resources.IngestReadingResource;
import com.primefuel.fulltank.platform.telemetry.interfaces.rest.transform.ReadingAckFromResultAssembler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Technical ingestion adapter. It is not a customer API: the caller authenticates as a device with its
 * rotating token (see T07-B), which is why this path is exempt from the user JWT filter chain.
 */
@RestController
@RequestMapping(value = "/api/v2/telemetry", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Telemetry", description = "Device telemetry ingestion (v2, machine authenticated)")
public class TelemetryController {

    private final TelemetryIngestServiceImpl ingestService;

    public TelemetryController(TelemetryIngestServiceImpl ingestService) {
        this.ingestService = ingestService;
    }

    /**
     * Ingests a single device reading.
     *
     * <p>Machine-authenticated with the {@code X-Device-Token} header (not the user JWT). The payload is
     * version-checked (currently only schema v1) and normalised. A reading whose credential is unknown,
     * revoked or has no active binding is still acknowledged (202) but stored as quarantined; a repeated
     * device/channel/sequence is acknowledged without writing. The endpoint applies no commercial policy.</p>
     */
    @Operation(summary = "Ingest a device reading",
            description = "Accepts a versioned, machine-authenticated telemetry reading; quarantine or duplicate readings are acknowledged rather than rejected.")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Reading accepted (or acknowledged as quarantine/duplicate)."),
            @ApiResponse(responseCode = "400", description = "Request body failed validation, the schema version is unsupported, or a required field/value is invalid.")
    })
    @PostMapping("/readings")
    public ResponseEntity<?> ingest(@RequestHeader(name = "X-Device-Token", required = false) String deviceToken,
                                    @Valid @RequestBody IngestReadingResource resource) {
        var result = ingestService.handle(new IngestTelemetryCommand(
                resource.schemaVersion(), resource.deviceId(), resource.channel(), resource.sequence(),
                resource.capturedAt(), resource.level(), resource.unit(), deviceToken));
        return ResponseEntityAssembler.toResponseEntityFromResult(
                result, ReadingAckFromResultAssembler::toResourceFromResult, HttpStatus.ACCEPTED);
    }
}
