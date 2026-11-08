package com.primefuel.fulltank.fog.infrastructure;

import com.primefuel.fulltank.fog.application.TelemetryForwarder;
import com.primefuel.fulltank.fog.config.FogProperties;
import com.primefuel.fulltank.fog.domain.Reading;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

/** Reenvía lecturas a {@code POST /api/telemetry/readings} con la credencial técnica del dispositivo. */
@Component
public class BackendTelemetryClient implements TelemetryForwarder {

    private static final Logger log = LoggerFactory.getLogger(BackendTelemetryClient.class);
    private static final String DEVICE_TOKEN_HEADER = "X-Device-Token";
    private static final int MAX_DETAIL_LENGTH = 300;

    private final RestClient restClient;
    private final String deviceToken;

    public BackendTelemetryClient(FogProperties properties) {
        var backend = properties.backend();
        // HTTP/1.1 y cuerpo en búfer: así la petición lleva Content-Length en vez de ir en chunks, que algunos
        // proxies (y servidores simples) no aceptan.
        var httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(backend.connectTimeout())
                .build();
        var requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(backend.readTimeout());
        this.restClient = RestClient.builder()
                .baseUrl(backend.baseUrl())
                .requestFactory(new BufferingClientHttpRequestFactory(requestFactory))
                .build();
        this.deviceToken = backend.deviceToken();
        if (deviceToken == null || deviceToken.isBlank()) {
            log.warn("DEVICE_TOKEN vacío: el backend aceptará las lecturas pero quedarán en CUARENTENA");
        }
    }

    record AckBody(Long readingId, String quality, Long tankId, boolean duplicate) {}

    @Override
    public ForwardResult forward(Reading reading) {
        try {
            return restClient.post()
                    .uri("/telemetry/readings")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .headers(h -> {
                        if (deviceToken != null && !deviceToken.isBlank()) {
                            h.set(DEVICE_TOKEN_HEADER, deviceToken);
                        }
                    })
                    .body(reading)
                    .exchange((request, response) -> {
                        int status = response.getStatusCode().value();
                        if (response.getStatusCode().is2xxSuccessful()) {
                            AckBody ack = response.bodyTo(AckBody.class);
                            return ack == null
                                    ? new ForwardResult.Accepted(null, null, null, false)
                                    : new ForwardResult.Accepted(ack.readingId(), ack.quality(), ack.tankId(),
                                    ack.duplicate());
                        }
                        String detail = truncate(response.bodyTo(String.class));
                        if (response.getStatusCode().is4xxClientError() && status != 408 && status != 429) {
                            return new ForwardResult.Rejected(status, detail);
                        }
                        return new ForwardResult.Retryable("HTTP " + status + ": " + detail);
                    });
        } catch (ResourceAccessException e) {
            return new ForwardResult.Retryable("sin respuesta del backend: " + e.getMessage());
        }
    }

    private static String truncate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= MAX_DETAIL_LENGTH ? text : text.substring(0, MAX_DETAIL_LENGTH) + "…";
    }
}
