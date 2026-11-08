package com.primefuel.fulltank.fog.interfaces.rest;

import com.primefuel.fulltank.fog.application.ReadingRelayService;
import com.primefuel.fulltank.fog.config.FogProperties;
import com.primefuel.fulltank.fog.infrastructure.MqttReadingSubscriber;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    public record MqttStatus(boolean enabled, boolean connected, String url, String topic) {}

    public record BackendStatus(String baseUrl, boolean deviceTokenConfigured) {}

    public record Health(String status, MqttStatus mqtt, BackendStatus backend, ReadingRelayService.Stats relay) {}

    private final ReadingRelayService relay;
    private final MqttReadingSubscriber mqtt;
    private final FogProperties.Backend backend;

    public HealthController(ReadingRelayService relay, MqttReadingSubscriber mqtt, FogProperties properties) {
        this.relay = relay;
        this.mqtt = mqtt;
        this.backend = properties.backend();
    }

    @GetMapping(value = "/health", produces = MediaType.APPLICATION_JSON_VALUE)
    public Health health() {
        var config = mqtt.config();
        return new Health("UP",
                new MqttStatus(config.enabled(), mqtt.isConnected(), config.url(), config.topic()),
                new BackendStatus(backend.baseUrl(),
                        backend.deviceToken() != null && !backend.deviceToken().isBlank()),
                relay.stats());
    }
}
