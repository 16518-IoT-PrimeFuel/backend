package com.primefuel.fulltank.edge;

import com.primefuel.fulltank.edge.config.EdgeProperties;
import com.primefuel.fulltank.edge.domain.Reading;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

public final class TestFixtures {

    public static final Instant NOW = Instant.parse("2026-10-21T22:00:00Z");

    private TestFixtures() {
    }

    public static EdgeProperties properties(List<String> allowedDeviceIds, int capacity) {
        return new EdgeProperties(
                new EdgeProperties.Backend("http://localhost/api", "token", Duration.ofSeconds(1), Duration.ofSeconds(1)),
                new EdgeProperties.Ingest(""),
                new EdgeProperties.Mqtt(false, "tcp://localhost:1883", "t/+/r", "", "", "", 1),
                new EdgeProperties.Policy(allowedDeviceIds, 0, 1000, Duration.ofMinutes(5)),
                new EdgeProperties.Queue(capacity, Duration.ofSeconds(2), Duration.ofSeconds(60)));
    }

    public static Reading reading(String deviceId, long sequence, double level) {
        return new Reading(1, deviceId, "level", sequence, NOW.minusSeconds(10), level, "LITRE");
    }

    /** Reloj que el test puede adelantar. */
    public static final class MutableClock extends Clock {
        private Instant now = NOW;

        public void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
