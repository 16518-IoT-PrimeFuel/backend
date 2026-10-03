package com.primefuel.fulltank.platform.provider;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.primefuel.fulltank.platform.shared.domain.model.valueobjects.*;
import com.primefuel.fulltank.platform.shared.domain.model.valueobjects.Unit;
import com.primefuel.fulltank.platform.telemetry.domain.model.aggregates.TelemetryReading;
import com.primefuel.fulltank.platform.telemetry.domain.model.valueobjects.ReadingQuality;
import com.primefuel.fulltank.platform.telemetry.domain.repositories.TelemetryReadingRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;

class ProviderTankReadingsTest extends ProviderReadTestSupport {
    @Autowired TelemetryReadingRepository readings;

    @Test
    void returnsAttributedAcceptedHistoryFiltersCaptureTimeAndRejectsOtherProvider()
            throws Exception {
        var f = fixture(true, false);
        var at = Instant.parse("2026-10-02T10:00:00Z");
        readings.save(
                new TelemetryReading(
                        1,
                        "old-sensor-" + f.tank(),
                        "level",
                        1,
                        at,
                        at.plusSeconds(5),
                        Volume.of(123, Unit.LITRE),
                        f.tank(),
                        f.org(),
                        ReadingQuality.ACCEPTED,
                        null));
        readings.save(
                new TelemetryReading(
                        1,
                        "quarantined-" + f.tank(),
                        "level",
                        2,
                        at,
                        at,
                        Volume.of(456, Unit.LITRE),
                        f.tank(),
                        f.org(),
                        ReadingQuality.QUARANTINED,
                        "INVALID"));
        mvc.perform(
                        get("/api/provider/tanks/{id}/readings", f.tank())
                                .param("from", at.toString())
                                .param("to", at.toString())
                                .with(provider(f.provider())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].level").value(123))
                .andExpect(jsonPath("$[0].capturedAt").value(at.toString()))
                .andExpect(jsonPath("$[0].quality").value("ACCEPTED"));
        mvc.perform(
                        get("/api/provider/tanks/{id}/readings", f.tank())
                                .with(provider(f.provider() + 50000)))
                .andExpect(status().isNotFound());
        mvc.perform(
                        get("/api/provider/tanks/{id}/readings", f.tank())
                                .param("from", at.plusSeconds(1).toString())
                                .with(provider(f.provider())))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
        mvc.perform(
                        get("/api/provider/tanks/{id}/readings", f.tank())
                                .param("from", "invalid")
                                .with(provider(f.provider())))
                .andExpect(status().isBadRequest());
    }
}
