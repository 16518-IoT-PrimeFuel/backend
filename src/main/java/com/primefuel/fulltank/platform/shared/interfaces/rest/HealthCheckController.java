package com.primefuel.fulltank.platform.shared.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Endpoint público para el keep-alive programado: ejecuta {@code SELECT 1} contra la base de datos. */
@RestController
@Tag(name = "Health check", description = "Estado de la API y de su conexión a la base de datos")
public class HealthCheckController {

    public record HealthCheckResource(String status, String database) {}

    private final JdbcTemplate jdbc;

    public HealthCheckController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Operation(summary = "Comprobar el estado de la API y de la base de datos")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "API y base de datos disponibles"),
            @ApiResponse(responseCode = "503", description = "La base de datos no responde")
    })
    @GetMapping(value = "/api/health-check", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<HealthCheckResource> healthCheck() {
        try {
            jdbc.queryForObject("SELECT 1", Integer.class);
            return ResponseEntity.ok(new HealthCheckResource("UP", "UP"));
        } catch (DataAccessException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new HealthCheckResource("DOWN", "DOWN"));
        }
    }
}
