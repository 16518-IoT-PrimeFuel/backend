package com.primefuel.fulltank.platform.telemetry.interfaces.rest;

import com.primefuel.fulltank.platform.shared.interfaces.rest.transform.ResponseEntityAssembler;
import com.primefuel.fulltank.platform.telemetry.application.commandservices.TelemetryIngestService;
import com.primefuel.fulltank.platform.telemetry.domain.model.commands.IngestTelemetryCommand;
import com.primefuel.fulltank.platform.telemetry.interfaces.rest.resources.IngestReadingResource;
import com.primefuel.fulltank.platform.telemetry.interfaces.rest.transform.ReadingAckFromResultAssembler;
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

    private final TelemetryIngestService ingestService;

    public TelemetryController(TelemetryIngestService ingestService) {
        this.ingestService = ingestService;
    }

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
