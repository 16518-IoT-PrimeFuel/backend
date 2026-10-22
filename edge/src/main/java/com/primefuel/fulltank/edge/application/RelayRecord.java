package com.primefuel.fulltank.edge.application;

import java.time.Instant;

/** Resultado final de una lectura en la Edge, para la vista de lecturas recientes. */
public record RelayRecord(
        String deviceId,
        String channel,
        long sequence,
        double level,
        String unit,
        Instant capturedAt,
        ReadingSource source,
        Status status,
        String quality,
        Long readingId,
        Long tankId,
        int attempts,
        Instant receivedAt,
        Instant finishedAt,
        String detail) {

    public enum Status { FORWARDED, REJECTED_BY_BACKEND, DROPPED_QUEUE_FULL }
}
