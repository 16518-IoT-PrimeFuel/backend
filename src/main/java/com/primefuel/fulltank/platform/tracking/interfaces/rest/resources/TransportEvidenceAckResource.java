package com.primefuel.fulltank.platform.tracking.interfaces.rest.resources;

import java.time.Instant;

/**
 * Acknowledgement of a recorded transport-evidence sample. {@code latestAdvanced} is {@code true} when the
 * sample moved the projection's latest trusted value and {@code false} for a late sample, which is still
 * stored as raw evidence (S16 invariant: late does not replace latest).
 */
public record TransportEvidenceAckResource(
        Long evidenceId,
        Long deliveryId,
        String kind,
        String milestone,
        boolean latestAdvanced,
        Instant recordedAt) {
}
