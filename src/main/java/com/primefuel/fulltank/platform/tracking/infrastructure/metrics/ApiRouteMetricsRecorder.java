package com.primefuel.fulltank.platform.tracking.infrastructure.metrics;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;

@Component
public class ApiRouteMetricsRecorder {
    private final JdbcTemplate jdbc;

    public ApiRouteMetricsRecorder(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void record(String routeKey, String version, String handler, String callerKey, Instant seenAt) {
        Timestamp timestamp = Timestamp.from(seenAt);
        if (jdbc.update("UPDATE api_route_metrics SET request_count = request_count + 1, last_seen = ? "
                + "WHERE route_key = ?", timestamp, routeKey) == 0) {
            try {
                jdbc.update("INSERT INTO api_route_metrics (route_key, version, handler, request_count, last_seen) "
                                + "VALUES (?, ?, ?, 1, ?)", routeKey, version, handler, timestamp);
            } catch (DuplicateKeyException concurrentInsert) {
                jdbc.update("UPDATE api_route_metrics SET request_count = request_count + 1, last_seen = ? "
                        + "WHERE route_key = ?", timestamp, routeKey);
            }
        }
        try {
            jdbc.update("INSERT INTO api_route_callers (route_key, caller_key) VALUES (?, ?)", routeKey, callerKey);
        } catch (DuplicateKeyException alreadyCounted) {
            // The composite primary key keeps distinct callers idempotent.
        }
    }
}
