package com.primefuel.fulltank.platform.telemetry;

import com.primefuel.fulltank.platform.equipment.devicebinding.application.commandservices.DeviceBindingCommandService;
import com.primefuel.fulltank.platform.equipment.devicebinding.application.internal.commandservices.DeviceCredentialServiceImpl;
import com.primefuel.fulltank.platform.equipment.devicebinding.domain.model.commands.BindDeviceCommand;
import com.primefuel.fulltank.platform.equipment.devicebinding.domain.model.commands.ProvisionDeviceCredentialCommand;
import com.primefuel.fulltank.platform.telemetry.api.events.ValidatedTankReadingEvent;
import com.primefuel.fulltank.platform.telemetry.application.internal.commandservices.TelemetryIngestServiceImpl;
import com.primefuel.fulltank.platform.telemetry.domain.model.commands.IngestTelemetryCommand;
import com.primefuel.fulltank.platform.telemetry.domain.model.valueobjects.ReadingQuality;
import com.primefuel.fulltank.platform.telemetry.domain.repositories.TelemetryReadingRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:telemetry_ingest;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "authorization.jwt.secret=0123456789abcdef0123456789abcdef"
})
@RecordApplicationEvents
@Transactional
class TelemetryIngestTest {

    private static final String DEVICE = "dev-t8";
    private static final String CHANNEL = "tank-level";
    private static final Instant CAPTURED = Instant.parse("2026-05-01T10:00:00Z");

    @Autowired
    private TelemetryIngestServiceImpl ingestService;

    @Autowired
    private DeviceCredentialServiceImpl credentialService;

    @Autowired
    private DeviceBindingCommandService bindingService;

    @Autowired
    private TelemetryReadingRepository repository;

    @Autowired
    private ApplicationEvents applicationEvents;

    private String provisionAndBind(Long tankId) {
        var credential = credentialService.handle(new ProvisionDeviceCredentialCommand(DEVICE, CHANNEL));
        assertThat(credential.isSuccess()).isTrue();
        assertThat(bindingService.handle(new BindDeviceCommand(1L, DEVICE, CHANNEL, tankId, CAPTURED)).isSuccess())
                .isTrue();
        return credential.getOrElse(null).rawToken();
    }

    @Test
    void storesAcceptedReadingsAndNeverDuplicatesAReplay() {
        var token = provisionAndBind(100L);

        var first = ingestService.handle(new IngestTelemetryCommand(
                1, DEVICE, CHANNEL, 1L, CAPTURED, 250.0, "LITRE", token));
        assertThat(first.isSuccess()).isTrue();
        assertThat(first.getOrElse(null).quality()).isEqualTo(ReadingQuality.ACCEPTED.name());
        assertThat(first.getOrElse(null).tankId()).isEqualTo(100L);
        assertThat(first.getOrElse(null).duplicate()).isFalse();

        assertThat(applicationEvents.stream(ValidatedTankReadingEvent.class)).hasSize(1);

        // A replay of the same sequence is acknowledged and writes nothing, even if the payload differs.
        var replay = ingestService.handle(new IngestTelemetryCommand(
                1, DEVICE, CHANNEL, 1L, CAPTURED, 999.0, "LITRE", token));
        assertThat(replay.getOrElse(null).duplicate()).isTrue();
        assertThat(repository.findByDeviceAndChannelOrderByCapturedAtAsc(DEVICE, CHANNEL)).hasSize(1);
        assertThat(repository.findByDeviceChannelAndSequence(DEVICE, CHANNEL, 1L).orElseThrow()
                .getLevel().amount()).isEqualTo(250.0);
        assertThat(applicationEvents.stream(ValidatedTankReadingEvent.class)).hasSize(1);
    }

    @Test
    void quarantinesUnknownCredentialsAndUnboundDevicesInsteadOfDroppingData() {
        var token = provisionAndBind(200L);

        var forged = ingestService.handle(new IngestTelemetryCommand(
                1, DEVICE, CHANNEL, 10L, CAPTURED, 100.0, "GAL", "forged"));
        assertThat(forged.isSuccess()).isTrue();
        assertThat(forged.getOrElse(null).quality()).isEqualTo(ReadingQuality.QUARANTINED.name());
        assertThat(repository.findByDeviceChannelAndSequence(DEVICE, CHANNEL, 10L).orElseThrow()
                .getQuarantineReason()).isEqualTo("UNKNOWN_CREDENTIAL");

        // Valid credential but a reading outside any binding period -> quarantine, not loss.
        var outOfPeriod = ingestService.handle(new IngestTelemetryCommand(
                1, DEVICE, CHANNEL, 11L, CAPTURED.minusSeconds(86400), 100.0, "GAL", token));
        assertThat(outOfPeriod.getOrElse(null).quality()).isEqualTo(ReadingQuality.QUARANTINED.name());
        assertThat(repository.findByDeviceChannelAndSequence(DEVICE, CHANNEL, 11L).orElseThrow()
                .getQuarantineReason()).isEqualTo("NO_ACTIVE_BINDING");

        assertThat(repository.countByQuality(ReadingQuality.QUARANTINED)).isEqualTo(2);
        assertThat(applicationEvents.stream(ValidatedTankReadingEvent.class)).isEmpty();
    }

    @Test
    void rejectsUnsupportedSchemaVersionsAndNonFiniteLevels() {
        var token = provisionAndBind(300L);

        assertThat(ingestService.handle(new IngestTelemetryCommand(
                2, DEVICE, CHANNEL, 20L, CAPTURED, 10.0, "GAL", token)).isFailure()).isTrue();
        assertThat(ingestService.handle(new IngestTelemetryCommand(
                1, DEVICE, CHANNEL, 21L, CAPTURED, Double.NaN, "GAL", token)).isFailure()).isTrue();
        assertThat(ingestService.handle(new IngestTelemetryCommand(
                1, DEVICE, CHANNEL, 22L, CAPTURED, -5.0, "GAL", token)).isFailure()).isTrue();
        assertThat(repository.findByDeviceAndChannelOrderByCapturedAtAsc(DEVICE, CHANNEL)).isEmpty();
    }
}
