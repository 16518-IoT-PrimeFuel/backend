package com.primefuel.fulltank.platform.tracking.api;

import com.primefuel.fulltank.platform.shared.application.result.ApplicationError;
import com.primefuel.fulltank.platform.shared.application.result.Result;
import com.primefuel.fulltank.platform.tracking.domain.model.commands.RecordLoadEvidenceCommand;
import com.primefuel.fulltank.platform.tracking.domain.model.commands.RecordPositionEvidenceCommand;

import java.time.Instant;

/**
 * Public write seam of the tracking module (S16/T16-A). It is the only surface through which transport
 * evidence enters the platform; nothing outside {@code tracking} may hold a tracking projection or a raw
 * evidence row.
 *
 * <p>The redesign (W6) makes the <em>driver app</em> the source of evidence: the caller authenticates as a
 * normal v2 principal and the REST adapter resolves the tenant and the assigned driver from the delivery
 * assignment — a {@code driverId} is never taken from the request body. Callers must therefore supply the
 * {@code providerId}/{@code driverId} they already resolved server-side, exactly like
 * {@code fleet.api.FleetReservations}.
 *
 * <p>Two shapes of evidence are accepted, matching the two payload variants of the endpoint:
 * <ul>
 *   <li>a position sample ({@link #recordPosition}) — publishes {@code DeliveryTelemetryReceived};</li>
 *   <li>a discrete load milestone ({@link #recordLoad}) — no domain event yet (the valve contract is T18).</li>
 * </ul>
 *
 * <p>A late sample (an observation older than the projection's latest one) is always preserved as raw
 * evidence but never moves the projection's <em>latest</em> value: the returned
 * {@link EvidenceAck#latestAdvanced()} reports whether the projection moved.
 */
public interface TransportEvidenceRecorder {

    Result<EvidenceAck, ApplicationError> recordPosition(RecordPositionEvidenceCommand command);

    Result<EvidenceAck, ApplicationError> recordLoad(RecordLoadEvidenceCommand command);

    record EvidenceAck(
            Long evidenceId,
            Long deliveryId,
            String kind,
            String milestone,
            boolean latestAdvanced,
            Instant recordedAt) {
    }
}
