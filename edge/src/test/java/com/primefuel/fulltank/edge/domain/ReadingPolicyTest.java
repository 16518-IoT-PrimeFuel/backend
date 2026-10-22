package com.primefuel.fulltank.edge.domain;

import com.primefuel.fulltank.edge.TestFixtures;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.primefuel.fulltank.edge.TestFixtures.reading;
import static org.assertj.core.api.Assertions.assertThat;

class ReadingPolicyTest {

    private final ReadingPolicy policy =
            new ReadingPolicy(TestFixtures.properties(List.of("esp32-tank-1"), 10), new TestFixtures.MutableClock());

    @Test
    void acceptsValidReading() {
        assertThat(policy.violation(reading("esp32-tank-1", 1, 500))).isEmpty();
    }

    @Test
    void rejectsUnknownDevice() {
        assertThat(policy.violation(reading("intruso", 1, 500))).hasValueSatisfying(
                reason -> assertThat(reason).contains("deviceId"));
    }

    @Test
    void rejectsLevelOutOfRange() {
        assertThat(policy.violation(reading("esp32-tank-1", 1, -1))).isPresent();
        assertThat(policy.violation(reading("esp32-tank-1", 1, 1001))).isPresent();
        assertThat(policy.violation(reading("esp32-tank-1", 1, Double.NaN))).isPresent();
    }

    @Test
    void rejectsUnsupportedSchemaAndFutureTimestamp() {
        var base = reading("esp32-tank-1", 1, 500);
        assertThat(policy.violation(new Reading(2, base.deviceId(), base.channel(), 1L, base.capturedAt(), 500.0,
                "LITRE"))).isPresent();
        assertThat(policy.violation(new Reading(1, base.deviceId(), base.channel(), 1L,
                TestFixtures.NOW.plusSeconds(3600), 500.0, "LITRE"))).isPresent();
    }

    @Test
    void emptyAllowListAcceptsAnyDevice() {
        var open = new ReadingPolicy(TestFixtures.properties(List.of(" ", ""), 10), new TestFixtures.MutableClock());
        assertThat(open.violation(reading("cualquiera", 1, 10))).isEmpty();
    }
}
