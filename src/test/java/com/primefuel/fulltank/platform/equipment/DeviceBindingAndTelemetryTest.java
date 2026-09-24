package com.primefuel.fulltank.platform.equipment;

import com.primefuel.fulltank.platform.equipment.application.internal.commandservices.DeviceBindingCommandService;
import com.primefuel.fulltank.platform.equipment.application.internal.commandservices.TelemetryCommandService;
import com.primefuel.fulltank.platform.equipment.application.internal.commandservices.RefillPolicyCommandService;
import com.primefuel.fulltank.platform.equipment.application.ports.DeviceBindingData;
import com.primefuel.fulltank.platform.equipment.application.ports.DeviceBindingStore;
import com.primefuel.fulltank.platform.equipment.application.ports.TankStore;
import com.primefuel.fulltank.platform.equipment.application.ports.TelemetryStore;
import com.primefuel.fulltank.platform.iam.application.internal.commandservices.CustomerSiteCommandService;
import com.primefuel.fulltank.platform.iam.application.ports.CustomerSiteStore;
import com.primefuel.fulltank.platform.iam.domain.repositories.BuyerCompanyRepository;
import com.primefuel.fulltank.platform.shared.application.events.DurableEventPublisher;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import com.primefuel.fulltank.platform.ordering.application.ports.AutomaticReplenishmentRequest;
import com.primefuel.fulltank.platform.ordering.application.ports.FuelRequestData;
import com.primefuel.fulltank.platform.ordering.domain.model.valueobjects.RequestStatus;
import com.primefuel.fulltank.platform.equipment.application.ports.RefillPolicyData;
import com.primefuel.fulltank.platform.equipment.application.ports.RefillEpisodeData;
import com.primefuel.fulltank.platform.equipment.application.ports.RefillPolicyStore;

class DeviceBindingAndTelemetryTest {
    private final CustomerSiteCommandService sites = new CustomerSiteCommandService(
            mock(BuyerCompanyRepository.class), mock(CustomerSiteStore.class));
    private final TankStore tanks = mock(TankStore.class);
    private final DeviceBindingStore bindings = mock(DeviceBindingStore.class);
    private final DurableEventPublisher events = mock(DurableEventPublisher.class);

    @Test
    void rejectsOverlappingDeviceIntervals() {
        var start = Instant.parse("2026-01-01T00:00:00Z");
        when(sites.siteBelongsToCompany(10L, 20L)).thenReturn(true);
        when(tanks.belongsToSite(30L, 10L)).thenReturn(true);
        when(bindings.findByDeviceAndChannel("sensor-1", "level"))
                .thenReturn(List.of(new DeviceBindingData(1L, "sensor-1", "level", 31L, 20L,
                        "hash", start, null, "ACTIVE")));
        var service = new DeviceBindingCommandService(sites, tanks, bindings, events);

        assertThrows(IllegalArgumentException.class, () -> service.bind(20L, 10L, 30L,
                "sensor-1", "level", "secret", start.plusSeconds(1), null));
        verify(bindings, never()).save(any());
    }

    @Test
    void telemetryRejectsNonFiniteLevelsBeforePersistence() {
        var telemetry = mock(TelemetryStore.class);
        var service = new TelemetryCommandService(mock(DeviceBindingCommandService.class), telemetry, tanks, events,
                mock(com.primefuel.fulltank.platform.equipment.application.internal.commandservices.RefillPolicyCommandService.class),
                mock(com.primefuel.fulltank.platform.equipment.application.ports.TelemetryInboxStore.class));

        assertThrows(IllegalArgumentException.class, () -> service.ingest("sensor-1", "level", "secret", 1,
                "event-1", "v1", Instant.now(), Double.NaN, "L", "GOOD"));
        verifyNoInteractions(telemetry);
    }

    @Test
    void telemetryInboxMakesReplayAcknowledgeWithoutReapplyingTank() {
        var telemetry = mock(TelemetryStore.class);
        var inbox = mock(com.primefuel.fulltank.platform.equipment.application.ports.TelemetryInboxStore.class);
        var bindingService = mock(DeviceBindingCommandService.class);
        var refillPolicies = mock(RefillPolicyCommandService.class);
        when(inbox.claim(eq("event-1"), eq("sensor-1"), eq("level"), eq(7L), any())).thenReturn(true, false);
        when(bindingService.resolve("sensor-1", "level", "secret", Instant.EPOCH))
                .thenReturn(new DeviceBindingData(1L, "sensor-1", "level", 30L, 20L, "hash",
                        Instant.EPOCH, null, "ACTIVE"));
        when(telemetry.saveIfAbsent(any())).thenReturn(true);
        var service = new TelemetryCommandService(bindingService, telemetry, tanks, events, refillPolicies, inbox);

        org.junit.jupiter.api.Assertions.assertTrue(service.ingest("sensor-1", "level", "secret", 7,
                "event-1", "v1", Instant.EPOCH, 40, "L", "GOOD"));
        org.junit.jupiter.api.Assertions.assertFalse(service.ingest("sensor-1", "level", "secret", 7,
                "event-1", "v1", Instant.EPOCH, 40, "L", "GOOD"));
        verify(telemetry, times(1)).saveIfAbsent(any());
        verify(tanks, times(1)).applyValidatedReading(eq(30L), eq(40D), any());
    }

    @Test
    void oneLowLevelEpisodeCreatesOnlyOneRequestUntilRecovery() {
        var policyStore = mock(RefillPolicyStore.class);
        var requestCreator = mock(AutomaticReplenishmentRequest.class);
        var policy = new RefillPolicyData(30L, 20L, 40L, 50L, 20, 5, 80, "Lima", true);
        var firstReading = Instant.parse("2026-02-01T00:00:00Z");
        var episode = new RefillEpisodeData("tank:30:episode:" + firstReading.toEpochMilli(), 30L,
                firstReading, Instant.now(), "REQUESTED", 44L);
        when(policyStore.findPolicy(30L)).thenReturn(java.util.Optional.of(policy));
        when(policyStore.findOpenEpisode(30L)).thenReturn(java.util.Optional.empty(), java.util.Optional.of(episode));
        when(policyStore.saveEpisode(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(requestCreator.create(any(com.primefuel.fulltank.platform.ordering.domain.model.commands.CreateFuelRequestCommand.class), anyString())).thenReturn(new FuelRequestData(44L, 20L, 40L, 30L, 50L,
                "DIESEL", "Diesel", 80D, "L", 1D, "Lima", java.time.LocalDate.now(),
                RequestStatus.PENDING, "AUTOMATIC", null, null, null));
        var service = new RefillPolicyCommandService(sites, tanks, policyStore, requestCreator, events);

        service.evaluate(30L, 10, "GOOD", firstReading);
        service.evaluate(30L, 9, "GOOD", firstReading.plusSeconds(60));

        verify(requestCreator, times(1)).create(any(com.primefuel.fulltank.platform.ordering.domain.model.commands.CreateFuelRequestCommand.class), anyString());
    }
}
