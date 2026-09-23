package com.primefuel.fulltank.platform.tracking.infrastructure.services;

import com.primefuel.fulltank.platform.shared.application.result.ApplicationError;
import com.primefuel.fulltank.platform.shared.application.result.Result;
import com.primefuel.fulltank.platform.shared.domain.model.valueobjects.Unit;
import com.primefuel.fulltank.platform.shared.domain.model.valueobjects.Volume;
import com.primefuel.fulltank.platform.shared.events.EventPublicationRegistry;
import com.primefuel.fulltank.platform.tracking.api.TransportEvidenceRecorder;
import com.primefuel.fulltank.platform.tracking.api.events.DeliveryTelemetryReceived;
import com.primefuel.fulltank.platform.tracking.domain.model.aggregates.DeliveryTracking;
import com.primefuel.fulltank.platform.tracking.domain.model.commands.RecordLoadEvidenceCommand;
import com.primefuel.fulltank.platform.tracking.domain.model.commands.RecordPositionEvidenceCommand;
import com.primefuel.fulltank.platform.tracking.domain.model.entities.TransportEvidenceSample;
import com.primefuel.fulltank.platform.tracking.domain.model.valueobjects.GeoPosition;
import com.primefuel.fulltank.platform.tracking.domain.model.valueobjects.LoadMilestone;
import com.primefuel.fulltank.platform.tracking.domain.repositories.DeliveryTrackingRepository;
import com.primefuel.fulltank.platform.tracking.domain.repositories.TransportEvidenceSampleRepository;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Transport-evidence recorder (S16/T16-A). Every accepted sample is stored twice by design: as a raw,
 * append-only {@link TransportEvidenceSample} (so a late or duplicate observation is never lost) and folded
 * into the {@link DeliveryTracking} projection (the latest <em>trusted</em> position / load milestone), whose
 * row is referenced by the sample that produced it. Both writes and the domain event happen in one
 * transaction, so a rollback leaves neither evidence nor event behind.
 *
 * <p>The late-sample rule is the aggregate's, not this service's: {@link DeliveryTracking#recordPosition}
 * returns whether the sample was newer, and an older one is persisted as raw evidence with
 * {@code latestAdvanced=false} while the projection stays put.
 */
@Component("transportEvidenceRecorder")
public class TransportEvidenceRecorderImpl implements TransportEvidenceRecorder {

    private static final String AGGREGATE_TYPE = "DeliveryTracking";
    static final String TELEMETRY_EVENT_TYPE = "delivery.telemetry.received.v1";
    private static final Duration MAX_CLOCK_SKEW = Duration.ofMinutes(2);

    private final DeliveryTrackingRepository trackingRepository;
    private final TransportEvidenceSampleRepository sampleRepository;
    private final EventPublicationRegistry publicationRegistry;
    private final Clock clock;

    public TransportEvidenceRecorderImpl(DeliveryTrackingRepository trackingRepository,
                                         TransportEvidenceSampleRepository sampleRepository,
                                         EventPublicationRegistry publicationRegistry,
                                         Clock clock) {
        this.trackingRepository = trackingRepository;
        this.sampleRepository = sampleRepository;
        this.publicationRegistry = publicationRegistry;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Result<EvidenceAck, ApplicationError> recordPosition(RecordPositionEvidenceCommand command) {
        if (command.deliveryId() == null) {
            return Result.failure(ApplicationError.validationError("deliveryId", "A delivery is required"));
        }
        if (command.recordedAt() == null) {
            return Result.failure(ApplicationError.validationError("recordedAt",
                    "A position timestamp is required"));
        }
        GeoPosition position;
        try {
            position = new GeoPosition(command.latitude(), command.longitude(), command.accuracyMeters());
        } catch (IllegalArgumentException exception) {
            return Result.failure(ApplicationError.validationError("position", exception.getMessage()));
        }

        var receivedAt = clock.instant();
        if (isBeyondClockSkew(command.recordedAt(), receivedAt)) {
            return Result.failure(futureTimestamp("recordedAt"));
        }
        try {
            var tracking = loadOrCreate(command.deliveryId(), command.providerId(), command.driverId());
            boolean advanced = tracking.recordPosition(position, command.recordedAt());

            var sample = sampleRepository.save(TransportEvidenceSample.position(
                    command.deliveryId(), command.providerId(), command.driverId(), position,
                    command.recordedAt(), receivedAt, advanced));
            if (advanced) {
                tracking.linkPositionEvidence(sample.getId());
            }
            tracking = trackingRepository.save(tracking);

            publicationRegistry.publish(TELEMETRY_EVENT_TYPE, AGGREGATE_TYPE, String.valueOf(tracking.getDeliveryId()),
                    tracking.getProviderId(), (long) tracking.getVersion(),
                    new DeliveryTelemetryReceived(sample.getId(), command.deliveryId(), command.providerId(),
                            command.driverId(), position.latitude(), position.longitude(), position.accuracyMeters(),
                            command.recordedAt(), advanced, receivedAt).toPayloadJson());

            return Result.success(new EvidenceAck(sample.getId(), command.deliveryId(), "POSITION", null,
                    advanced, command.recordedAt()));
        } catch (OptimisticLockingFailureException exception) {
            return concurrent();
        }
    }

    @Override
    @Transactional
    public Result<EvidenceAck, ApplicationError> recordLoad(RecordLoadEvidenceCommand command) {
        if (command.deliveryId() == null) {
            return Result.failure(ApplicationError.validationError("deliveryId", "A delivery is required"));
        }
        if (command.milestone() == null) {
            return Result.failure(ApplicationError.validationError("milestone", "A load milestone is required"));
        }
        Volume volume = null;
        if (command.volume() != null) {
            if (command.volume() <= 0) {
                return Result.failure(ApplicationError.validationError("volume",
                        "The declared volume must be greater than zero"));
            }
            try {
                volume = Volume.of(command.volume(), Unit.fromCode(command.unit()));
            } catch (IllegalArgumentException exception) {
                return Result.failure(ApplicationError.validationError("volume", exception.getMessage()));
            }
        }
        var recordedAt = command.recordedAt() != null ? command.recordedAt() : clock.instant();
        if (isBeyondClockSkew(recordedAt, clock.instant())) {
            return Result.failure(futureTimestamp("recordedAt"));
        }

        try {
            var tracking = loadOrCreate(command.deliveryId(), command.providerId(), command.driverId());
            boolean advanced;
            try {
                advanced = tracking.recordLoad(command.milestone(), volume, recordedAt);
            } catch (IllegalStateException exception) {
                return Result.failure(ApplicationError.businessRuleViolation("transportEvidence.load",
                        exception.getMessage()));
            }

            // The raw sample is always kept, exactly like a position sample; only an advancing one is linked
            // as the source of the projection's latest load state.
            var sample = sampleRepository.save(TransportEvidenceSample.load(
                    command.deliveryId(), command.providerId(), command.driverId(), command.milestone(),
                    volume, recordedAt, clock.instant(), advanced));
            if (advanced) {
                tracking.linkLoadEvidence(sample.getId());
            }
            tracking = trackingRepository.save(tracking);

            return Result.success(new EvidenceAck(sample.getId(), command.deliveryId(), "LOAD",
                    command.milestone().name(), advanced, recordedAt));
        } catch (OptimisticLockingFailureException exception) {
            return concurrent();
        }
    }

    /**
     * Loads the projection of a delivery or creates it. The driver is refreshed to the current assignment,
     * since a delivery may be reassigned and the evidence must be attributed to whoever reported it.
     */
    private DeliveryTracking loadOrCreate(Long deliveryId, Long providerId, Long driverId) {
        return trackingRepository.findByDeliveryId(deliveryId)
                .map(existing -> {
                    existing.setDriverId(driverId);
                    return existing;
                })
                .orElseGet(() -> new DeliveryTracking(deliveryId, providerId, driverId));
    }

    /** A client clock ahead of the server beyond the skew would pin itself as the latest sample forever. */
    private static boolean isBeyondClockSkew(Instant recordedAt, Instant now) {
        return recordedAt.isAfter(now.plus(MAX_CLOCK_SKEW));
    }

    private static ApplicationError futureTimestamp(String field) {
        return ApplicationError.validationError(field,
                "The timestamp is in the future beyond the tolerated clock skew of " + MAX_CLOCK_SKEW.toMinutes()
                        + " minutes");
    }

    private static Result<EvidenceAck, ApplicationError> concurrent() {
        return Result.failure(ApplicationError.conflict("DeliveryTracking",
                "The tracking projection was updated concurrently; retry the evidence"));
    }
}
