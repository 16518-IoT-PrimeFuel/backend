package com.primefuel.fulltank.platform.telemetry.application.internal.commandservices;

import com.primefuel.fulltank.platform.equipment.api.DeviceAuthentication;
import com.primefuel.fulltank.platform.shared.application.result.ApplicationError;
import com.primefuel.fulltank.platform.shared.application.result.Result;
import com.primefuel.fulltank.platform.shared.domain.model.valueobjects.Unit;
import com.primefuel.fulltank.platform.shared.domain.model.valueobjects.Volume;
import com.primefuel.fulltank.platform.telemetry.api.events.ValidatedTankReadingEvent;
import com.primefuel.fulltank.platform.telemetry.domain.model.aggregates.TelemetryReading;
import com.primefuel.fulltank.platform.telemetry.domain.model.commands.IngestTelemetryCommand;
import com.primefuel.fulltank.platform.telemetry.domain.model.valueobjects.ReadingQuality;
import com.primefuel.fulltank.platform.telemetry.domain.repositories.TelemetryReadingRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Protocol adapter, observation only: it version-checks and normalises the payload, deduplicates by
 * device/channel/sequence, authenticates the machine and stores the reading. It applies **no commercial
 * policy** (no thresholds, no orders) — that is S09's job.
 */
@Service
public class TelemetryIngestServiceImpl {
    public record IngestResult(Long readingId, String quality, Long tankId, boolean duplicate) { }

    public static final int SUPPORTED_SCHEMA_VERSION = 1;

    private final TelemetryReadingRepository repository;
    private final DeviceAuthentication deviceAuthentication;
    private final ApplicationEventPublisher events;

    public TelemetryIngestServiceImpl(TelemetryReadingRepository repository,
                                      DeviceAuthentication deviceAuthentication,
                                      ApplicationEventPublisher events) {
        this.repository = repository;
        this.deviceAuthentication = deviceAuthentication;
        this.events = events;
    }
    @Transactional
    public Result<IngestResult, ApplicationError> handle(IngestTelemetryCommand command) {
        if (command.schemaVersion() == null || command.schemaVersion() != SUPPORTED_SCHEMA_VERSION) {
            return Result.failure(ApplicationError.validationError(
                    "schemaVersion", "Unsupported telemetry schema version: " + command.schemaVersion()));
        }
        if (command.sequence() == null) {
            return Result.failure(ApplicationError.validationError("sequence", "A device sequence is required"));
        }
        if (command.capturedAt() == null) {
            return Result.failure(ApplicationError.validationError("capturedAt", "A capture instant is required"));
        }

        // Replay safety: a sequence already stored is acknowledged without writing anything.
        var existing = repository.findByDeviceChannelAndSequence(
                command.deviceId(), command.channel(), command.sequence());
        if (existing.isPresent()) {
            return Result.success(new IngestResult(
                    existing.get().getId(), existing.get().getQuality().name(),
                    existing.get().getTankId(), true));
        }

        Volume level;
        try {
            level = Volume.of(command.level() == null ? Double.NaN : command.level(), Unit.fromCode(command.unit()));
        } catch (IllegalArgumentException exception) {
            return Result.failure(ApplicationError.validationError("level", exception.getMessage()));
        }

        var decision = deviceAuthentication.authenticate(
                command.deviceId(), command.channel(), command.token(), command.capturedAt());
        var quarantineReason = decision.authenticated() ? null : decision.outcome();
        var quality = decision.authenticated() ? ReadingQuality.ACCEPTED : ReadingQuality.QUARANTINED;

        TelemetryReading reading;
        try {
            reading = new TelemetryReading(
                    command.schemaVersion(), command.deviceId(), command.channel(), command.sequence(),
                    command.capturedAt(), Instant.now(), level,
                    decision.tankId(), decision.organizationId(), quality, quarantineReason);
        } catch (IllegalArgumentException exception) {
            return Result.failure(ApplicationError.validationError("reading", exception.getMessage()));
        }

        try {
            var saved = repository.save(reading);
            if (saved.isAccepted()) {
                events.publishEvent(new ValidatedTankReadingEvent(
                        saved.getId(), saved.getDeviceId(), saved.getChannel(), saved.getSequence(),
                        saved.getTankId(), saved.getOrganizationId(),
                        saved.getLevel().amount(), saved.getLevel().unit().name(), saved.getCapturedAt()));
            }
            return Result.success(new IngestResult(
                    saved.getId(), saved.getQuality().name(), saved.getTankId(), false));
        } catch (DataIntegrityViolationException exception) {
            // Concurrent replay: the unique (device, channel, sequence) won the race, nothing duplicated.
            return Result.success(new IngestResult(null, ReadingQuality.QUARANTINED.name(), null, true));
        }
    }
}
