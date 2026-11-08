package com.primefuel.fulltank.fog.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * Configuración de la Fog API. Los valores sensibles (token del dispositivo, claves) llegan por variables de entorno.
 */
@ConfigurationProperties(prefix = "fog")
public record FogProperties(Backend backend, Ingest ingest, Mqtt mqtt, Policy policy, Queue queue) {

    public record Backend(String baseUrl, String deviceToken, Duration connectTimeout, Duration readTimeout) {}

    public record Ingest(String apiKey) {}

    public record Mqtt(boolean enabled, String url, String topic, String username, String password,
                       String clientId, int qos) {}

    public record Policy(List<String> allowedDeviceIds, double minLevel, double maxLevel, Duration maxClockSkew) {
        public Policy {
            allowedDeviceIds = allowedDeviceIds == null
                    ? List.of()
                    : allowedDeviceIds.stream().map(String::trim).filter(s -> !s.isEmpty()).toList();
        }
    }

    public record Queue(int capacity, Duration initialBackoff, Duration maxBackoff) {}
}
