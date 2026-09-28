package com.primefuel.fulltank.platform.tracking.domain.model.commands;

import com.primefuel.fulltank.platform.tracking.domain.model.valueobjects.LoadMilestone;

import java.time.Instant;

/**
 * A discrete load milestone to record for a delivery (S16/T16-A). The volume is optional and free-form
 * (the driver's declared quantity); {@code unit} maps onto the shared {@code Unit} when a volume is present.
 * {@code providerId}/{@code driverId} are resolved server-side, never taken from the request body.
 */
public record RecordLoadEvidenceCommand(
        Long deliveryId,
        Long providerId,
        Long driverId,
        LoadMilestone milestone,
        Double volume,
        String unit,
        Instant recordedAt,
        String eventId) {
}
