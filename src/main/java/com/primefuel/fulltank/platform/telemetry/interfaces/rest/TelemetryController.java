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
 * Adaptador técnico de ingesta, no destinado a clientes. El dispositivo se autentica con un token rotativo,
 * separado de la cadena de autenticación JWT de usuarios.
 */
@RestController
@RequestMapping(value = "/api/telemetry", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Telemetría", description = "Recepción de mediciones autenticadas de dispositivos")
public class TelemetryController {

    private final TelemetryIngestServiceImpl ingestService;

    public TelemetryController(TelemetryIngestServiceImpl ingestService) {
        this.ingestService = ingestService;
    }

    /**
     * Recibe una medición de un dispositivo.
     *
     * <p>Se autentica con {@code X-Device-Token}, no con JWT de usuario. El esquema se valida y normaliza.
     * Las credenciales desconocidas, revocadas o sin vínculo activo se confirman con 202 y quedan en cuarentena;
     * una secuencia repetida se confirma sin duplicar el registro.</p>
     */
    @Operation(summary = "Recibir medición de dispositivo",
            description = "Acepta una medición de telemetría versionada y autenticada por dispositivo; confirma lecturas en cuarentena o duplicadas sin almacenarlas como nuevas.")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Medición aceptada o confirmada como lectura en cuarentena o duplicada."),
            @ApiResponse(responseCode = "400", description = "El cuerpo no supera la validación, la versión de esquema no está admitida o falta un dato requerido.")
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
