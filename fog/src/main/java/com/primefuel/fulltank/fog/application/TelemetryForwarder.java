package com.primefuel.fulltank.fog.application;

import com.primefuel.fulltank.fog.domain.Reading;

/** Puerto de salida hacia la ingesta del backend. */
public interface TelemetryForwarder {

    ForwardResult forward(Reading reading);

    sealed interface ForwardResult {

        /** El backend respondió 2xx. Ojo: un 202 con quality QUARANTINED indica token malo o tanque sin vínculo. */
        record Accepted(Long readingId, String quality, Long tankId, boolean duplicate) implements ForwardResult {}

        /** El backend rechazó la lectura (4xx); reenviarla no cambiaría nada. */
        record Rejected(int status, String detail) implements ForwardResult {}

        /** Sin respuesta, timeout o 5xx: se reintenta la misma lectura. */
        record Retryable(String detail) implements ForwardResult {}
    }
}
