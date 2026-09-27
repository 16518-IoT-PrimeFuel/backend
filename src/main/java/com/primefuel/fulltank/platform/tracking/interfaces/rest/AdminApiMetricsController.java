package com.primefuel.fulltank.platform.tracking.interfaces.rest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/admin/api-metrics")
@Tag(name = "Métricas de rutas API", description = "Consulta administrativa del uso de rutas")
public class AdminApiMetricsController {
    private final JdbcTemplate jdbc;

    public AdminApiMetricsController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Devuelve conteos por ruta y usuarios tenant distintos; permite filtrar por versión de API. */
    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @Operation(summary = "Listar uso de rutas API",
            description = "Devuelve por patrón la cantidad de solicitudes, el último acceso y el número de usuarios distintos. Solo para administradores.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Métricas de rutas devueltas."),
            @ApiResponse(responseCode = "400", description = "La versión debe ser v1 o v2."),
            @ApiResponse(responseCode = "403", description = "El usuario no tiene autoridad de administrador de plataforma.")
    })
    public ResponseEntity<?> list(@RequestParam(required = false) String version) {
        if (version != null && !version.equals("v1") && !version.equals("v2")) {
            return ResponseEntity.badRequest().build();
        }
        var result = jdbc.query("SELECT m.route_key, m.version, m.handler, m.request_count, m.last_seen, "
                        + "COUNT(c.caller_key) AS distinct_callers FROM api_route_metrics m "
                        + "LEFT JOIN api_route_callers c ON c.route_key = m.route_key "
                        + "WHERE (? IS NULL OR m.version = ?) "
                        + "GROUP BY m.route_key, m.version, m.handler, m.request_count, m.last_seen "
                        + "ORDER BY m.version, m.route_key",
                (row, index) -> new ApiRouteMetric(row.getString("route_key"), row.getString("version"),
                        row.getString("handler"), row.getLong("request_count"),
                        row.getTimestamp("last_seen").toLocalDateTime(), row.getLong("distinct_callers")),
                version, version);
        return ResponseEntity.ok(result);
    }

    public record ApiRouteMetric(String routeKey, String version, String handler, long count,
                                 LocalDateTime lastSeen, long distinctCallers) { }
}
