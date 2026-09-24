package com.primefuel.fulltank.platform.equipment.interfaces.rest;

import com.primefuel.fulltank.platform.equipment.application.internal.commandservices.TelemetryCommandService;
import com.primefuel.fulltank.platform.equipment.interfaces.rest.resources.IngestTelemetryResource;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping(value = "/api/v2/telemetry", produces = MediaType.APPLICATION_JSON_VALUE)
public class TelemetryController {
    private final TelemetryCommandService service;

    public TelemetryController(TelemetryCommandService service) {
        this.service = service;
    }

    @PostMapping(value = "/readings", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> ingest(@RequestHeader("X-Device-Credential") String credential,
                                    @Valid @RequestBody IngestTelemetryResource resource) {
        try {
            var accepted = service.ingest(resource.deviceId(), resource.channel(), credential,
                    resource.sequenceNumber(), resource.eventId(), resource.schemaVersion(), resource.capturedAt(),
                    resource.levelValue(), resource.unit(), resource.quality());
            return ResponseEntity.ok(Map.of("accepted", accepted, "duplicate", !accepted));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
        }
    }
}
