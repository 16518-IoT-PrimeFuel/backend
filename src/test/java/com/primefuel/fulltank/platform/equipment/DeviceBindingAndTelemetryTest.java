package com.primefuel.fulltank.platform.equipment;

import com.primefuel.fulltank.platform.equipment.application.internal.commandservices.DeviceBindingCommandService;
import com.primefuel.fulltank.platform.equipment.application.internal.commandservices.TelemetryCommandService;
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
        var service = new TelemetryCommandService(mock(DeviceBindingCommandService.class), telemetry, tanks, events);

        assertThrows(IllegalArgumentException.class, () -> service.ingest("sensor-1", "level", "secret", 1,
                "event-1", "v1", Instant.now(), Double.NaN, "L", "GOOD"));
        verifyNoInteractions(telemetry);
    }
}
