package com.primefuel.fulltank.edge.interfaces.rest;

import com.primefuel.fulltank.edge.application.ReadingRejectedException;
import com.primefuel.fulltank.edge.application.ReadingRelayService;
import com.primefuel.fulltank.edge.application.ReadingSource;
import com.primefuel.fulltank.edge.application.RelayRecord;
import com.primefuel.fulltank.edge.config.EdgeProperties;
import com.primefuel.fulltank.edge.domain.Reading;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * Entrada HTTP del dispositivo a la Edge (red local). Mismo cuerpo JSON que la ingesta del backend.
 *
 * <p>Responde 202 en cuanto la lectura queda encolada; el reenvío al backend es asíncrono. 400 significa que la
 * lectura no debe reenviarse: el dispositivo la descarta.</p>
 */
@RestController
@RequestMapping(value = "/api/v1/readings", produces = MediaType.APPLICATION_JSON_VALUE)
public class ReadingsController {

    private static final Logger log = LoggerFactory.getLogger(ReadingsController.class);

    public record AcceptResponse(String status, int queued) {}

    private final ReadingRelayService relay;
    private final byte[] apiKey;

    public ReadingsController(ReadingRelayService relay, EdgeProperties properties) {
        this.relay = relay;
        String key = properties.ingest().apiKey();
        this.apiKey = key == null || key.isBlank() ? null : key.getBytes(StandardCharsets.UTF_8);
        if (apiKey == null) {
            log.warn("EDGE_API_KEY vacía: POST /api/v1/readings acepta lecturas sin clave (solo para pruebas)");
        }
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AcceptResponse> ingest(@RequestHeader(name = "X-Edge-Key", required = false) String key,
                                                 @Valid @RequestBody Reading reading) {
        if (apiKey != null && (key == null
                || !MessageDigest.isEqual(apiKey, key.getBytes(StandardCharsets.UTF_8)))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "X-Edge-Key inválida o ausente");
        }
        var outcome = relay.accept(reading, ReadingSource.HTTP);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new AcceptResponse(outcome.name(), relay.stats().queued()));
    }

    @GetMapping("/recent")
    public List<RelayRecord> recent() {
        return relay.recent();
    }

    @ExceptionHandler(ReadingRejectedException.class)
    public ProblemDetail onRejected(ReadingRejectedException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }
}
