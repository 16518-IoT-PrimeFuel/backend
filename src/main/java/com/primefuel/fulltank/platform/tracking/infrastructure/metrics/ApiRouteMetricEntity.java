package com.primefuel.fulltank.platform.tracking.infrastructure.metrics;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "api_route_metrics")
@Getter
@Setter
@NoArgsConstructor
public class ApiRouteMetricEntity {
    @Id
    @Column(name = "route_key", nullable = false, length = 300)
    private String routeKey;

    @Column(nullable = false, length = 5)
    private String version;

    @Column(nullable = false, length = 200)
    private String handler;

    @Column(name = "request_count", nullable = false)
    private long requestCount;

    @Column(name = "last_seen", nullable = false)
    private LocalDateTime lastSeen;
}
