package com.primefuel.fulltank.edge.infrastructure;

import com.primefuel.fulltank.edge.application.ReadingRejectedException;
import com.primefuel.fulltank.edge.application.ReadingRelayService;
import com.primefuel.fulltank.edge.application.ReadingSource;
import com.primefuel.fulltank.edge.config.EdgeProperties;
import com.primefuel.fulltank.edge.domain.Reading;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Se suscribe al topic de lecturas del broker (público o Mosquitto) y las entrega al relay.
 *
 * <p>El broker es complementario: si no está disponible, la Edge sigue recibiendo lecturas por HTTP y reintenta la
 * conexión en segundo plano. Nunca lanza excepciones desde {@link #messageArrived}, porque Paho cerraría la
 * conexión.</p>
 */
@Component
public class MqttReadingSubscriber implements SmartLifecycle, MqttCallbackExtended {

    private static final Logger log = LoggerFactory.getLogger(MqttReadingSubscriber.class);

    private final EdgeProperties.Mqtt config;
    private final ReadingRelayService relay;
    private final ObjectMapper objectMapper;
    private final Validator validator;
    private final String clientId;

    private MqttClient client;
    private volatile boolean running;

    public MqttReadingSubscriber(EdgeProperties properties, ReadingRelayService relay, ObjectMapper objectMapper,
                                 Validator validator) {
        this.config = properties.mqtt();
        this.relay = relay;
        this.objectMapper = objectMapper;
        this.validator = validator;
        this.clientId = config.clientId() == null || config.clientId().isBlank()
                ? "fulltank-edge-" + UUID.randomUUID().toString().substring(0, 8)
                : config.clientId();
    }

    @Override
    public void start() {
        if (!config.enabled()) {
            log.info("MQTT deshabilitado: la Edge solo recibe lecturas por HTTP");
            return;
        }
        try {
            client = new MqttClient(config.url(), clientId, new MemoryPersistence());
            client.setCallback(this);
        } catch (MqttException e) {
            log.error("URL de broker MQTT inválida '{}': {}", config.url(), e.getMessage());
            return;
        }
        running = true;
        ensureConnected();
    }

    /** Reintenta la primera conexión; después Paho reconecta solo. */
    @Scheduled(fixedDelay = 15_000, initialDelay = 15_000)
    public synchronized void ensureConnected() {
        if (!running || client == null || client.isConnected()) {
            return;
        }
        try {
            log.info("Conectando a {} como {}", config.url(), clientId);
            client.connect(connectOptions());
        } catch (MqttException e) {
            log.warn("No se pudo conectar a {}: {}. Reintento en 15 s", config.url(), e.getMessage());
        }
    }

    private MqttConnectOptions connectOptions() {
        var options = new MqttConnectOptions();
        options.setAutomaticReconnect(true);
        options.setCleanSession(true);
        options.setConnectionTimeout(10);
        options.setKeepAliveInterval(30);
        if (config.username() != null && !config.username().isBlank()) {
            options.setUserName(config.username());
            options.setPassword(config.password() == null ? new char[0] : config.password().toCharArray());
        }
        return options;
    }

    @Override
    public void connectComplete(boolean reconnect, String serverURI) {
        // Con sesión limpia, cada (re)conexión exige volver a suscribirse.
        try {
            client.subscribe(config.topic(), config.qos());
            log.info("{} a {}; suscrito a '{}' (QoS {})", reconnect ? "Reconectado" : "Conectado", serverURI,
                    config.topic(), config.qos());
        } catch (MqttException e) {
            log.error("No se pudo suscribir a '{}': {}", config.topic(), e.getMessage());
        }
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        try {
            Reading reading = objectMapper.readValue(message.getPayload(), Reading.class);
            Set<ConstraintViolation<Reading>> violations = validator.validate(reading);
            if (!violations.isEmpty()) {
                log.warn("Mensaje en '{}' descartado por formato: {}", topic, violations.stream()
                        .map(v -> v.getPropertyPath() + " " + v.getMessage())
                        .collect(Collectors.joining(", ")));
                return;
            }
            relay.accept(reading, ReadingSource.MQTT);
        } catch (ReadingRejectedException e) {
            // Ya registrado por el relay.
        } catch (Exception e) {
            log.warn("Mensaje en '{}' descartado: JSON inválido ({})", topic, e.getMessage());
        }
    }

    @Override
    public void connectionLost(Throwable cause) {
        log.warn("Conexión MQTT perdida: {}. Paho reintentará", cause == null ? "desconocido" : cause.getMessage());
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        // La Edge solo se suscribe; no publica.
    }

    public boolean isConnected() {
        return client != null && client.isConnected();
    }

    public EdgeProperties.Mqtt config() {
        return config;
    }

    @Override
    public synchronized void stop() {
        running = false;
        if (client == null) {
            return;
        }
        try {
            if (client.isConnected()) {
                client.disconnect();
            }
            client.close();
        } catch (MqttException e) {
            log.debug("Error al cerrar el cliente MQTT: {}", e.getMessage());
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
