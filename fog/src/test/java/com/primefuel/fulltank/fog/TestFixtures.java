package com.primefuel.fulltank.fog;

import com.primefuel.fulltank.fog.config.FogProperties;
import com.primefuel.fulltank.fog.domain.Reading;

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

    public static FogProperties properties(List<String> allowedDeviceIds, int capacity) {
        return new FogProperties(
                new FogProperties.Backend("http://localhost/api", "token", Duration.ofSeconds(1), Duration.ofSeconds(1)),
                new FogProperties.Ingest(""),
                new FogProperties.Mqtt(false, "tcp://localhost:1883", "t/+/r", "", "", "", 1),
                new FogProperties.Policy(allowedDeviceIds, 0, 1000, Duration.ofMinutes(5)),
                new FogProperties.Queue(capacity, Duration.ofSeconds(2), Duration.ofSeconds(60)));
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
