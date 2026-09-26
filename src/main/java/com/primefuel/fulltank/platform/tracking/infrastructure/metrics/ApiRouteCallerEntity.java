package com.primefuel.fulltank.platform.tracking.infrastructure.metrics;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.Objects;

@Entity
@Table(name = "api_route_callers")
@IdClass(ApiRouteCallerEntity.Key.class)
@Getter
@Setter
@NoArgsConstructor
public class ApiRouteCallerEntity {
    @Id
    @Column(name = "route_key", nullable = false, length = 300)
    private String routeKey;

    @Id
    @Column(name = "caller_key", nullable = false, length = 80)
    private String callerKey;

    public static class Key implements Serializable {
        public String routeKey;
        public String callerKey;

        public Key() { }

        @Override public boolean equals(Object other) {
            return other instanceof Key key && Objects.equals(routeKey, key.routeKey)
                    && Objects.equals(callerKey, key.callerKey);
        }

        @Override public int hashCode() { return Objects.hash(routeKey, callerKey); }
    }
}
