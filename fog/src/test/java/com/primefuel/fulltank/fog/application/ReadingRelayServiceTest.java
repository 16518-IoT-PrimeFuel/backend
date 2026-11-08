package com.primefuel.fulltank.fog.application;

import com.primefuel.fulltank.fog.TestFixtures;
import com.primefuel.fulltank.fog.application.TelemetryForwarder.ForwardResult;
import com.primefuel.fulltank.fog.domain.Reading;
import com.primefuel.fulltank.fog.domain.ReadingPolicy;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import static com.primefuel.fulltank.fog.TestFixtures.reading;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReadingRelayServiceTest {

    private final TestFixtures.MutableClock clock = new TestFixtures.MutableClock();
    private final ScriptedForwarder forwarder = new ScriptedForwarder();

    private ReadingRelayService relay(int capacity) {
        var properties = TestFixtures.properties(List.of(), capacity);
        return new ReadingRelayService(new ReadingPolicy(properties, clock), forwarder, properties, clock);
    }

    @Test
    void forwardsInArrivalOrder() {
        var relay = relay(10);
        relay.accept(reading("d", 1, 10), ReadingSource.HTTP);
        relay.accept(reading("d", 2, 20), ReadingSource.MQTT);

        relay.drain();

        assertThat(forwarder.sent).extracting(Reading::sequence).containsExactly(1L, 2L);
        assertThat(relay.stats().forwarded()).isEqualTo(2);
        assertThat(relay.stats().queued()).isZero();
        assertThat(relay.recent()).extracting(RelayRecord::status)
                .containsOnly(RelayRecord.Status.FORWARDED);
    }

    @Test
    void retriesSameReadingWithBackoffAndKeepsOrder() {
        var relay = relay(10);
        relay.accept(reading("d", 1, 10), ReadingSource.HTTP);
        relay.accept(reading("d", 2, 20), ReadingSource.HTTP);
        forwarder.script.add(new ForwardResult.Retryable("Render dormido"));

        relay.drain();
        assertThat(forwarder.sent).extracting(Reading::sequence).containsExactly(1L);
        assertThat(relay.stats().queued()).isEqualTo(2);

        relay.drain(); // aún en backoff: no reintenta
        assertThat(forwarder.sent).hasSize(1);

        clock.advance(Duration.ofSeconds(2));
        relay.drain();
        assertThat(forwarder.sent).extracting(Reading::sequence).containsExactly(1L, 1L, 2L);
        assertThat(relay.recent().getLast().attempts()).isEqualTo(2);
    }

    @Test
    void dropsReadingRejectedByBackendAndContinues() {
        var relay = relay(10);
        relay.accept(reading("d", 1, 10), ReadingSource.HTTP);
        relay.accept(reading("d", 2, 20), ReadingSource.HTTP);
        forwarder.script.add(new ForwardResult.Rejected(400, "bad"));

        relay.drain();

        assertThat(forwarder.sent).extracting(Reading::sequence).containsExactly(1L, 2L);
        assertThat(relay.stats().rejected()).isEqualTo(1);
        assertThat(relay.stats().lastError()).isNull();
    }

    @Test
    void countsQuarantinedAcks() {
        var relay = relay(10);
        relay.accept(reading("d", 1, 10), ReadingSource.MQTT);
        forwarder.script.add(new ForwardResult.Accepted(7L, "QUARANTINED", null, false));

        relay.drain();

        assertThat(relay.stats().quarantined()).isEqualTo(1);
        assertThat(relay.recent().getFirst().quality()).isEqualTo("QUARANTINED");
    }

    @Test
    void ignoresRepeatedSequence() {
        var relay = relay(10);
        assertThat(relay.accept(reading("d", 1, 10), ReadingSource.MQTT))
                .isEqualTo(ReadingRelayService.AcceptOutcome.QUEUED);
        assertThat(relay.accept(reading("d", 1, 10), ReadingSource.HTTP))
                .isEqualTo(ReadingRelayService.AcceptOutcome.DUPLICATE);
        assertThat(relay.stats().queued()).isEqualTo(1);
    }

    @Test
    void dropsOldestWhenQueueIsFull() {
        var relay = relay(2);
        relay.accept(reading("d", 1, 10), ReadingSource.HTTP);
        relay.accept(reading("d", 2, 20), ReadingSource.HTTP);
        relay.accept(reading("d", 3, 30), ReadingSource.HTTP);

        relay.drain();

        assertThat(forwarder.sent).extracting(Reading::sequence).containsExactly(2L, 3L);
        assertThat(relay.stats().dropped()).isEqualTo(1);
    }

    @Test
    void rejectsReadingThatViolatesPolicy() {
        var relay = relay(10);
        assertThatThrownBy(() -> relay.accept(reading("d", 1, -5), ReadingSource.HTTP))
                .isInstanceOf(ReadingRejectedException.class);
        assertThat(relay.stats().queued()).isZero();
    }

    @Test
    void backoffGrowsAndIsCapped() {
        var relay = relay(10);
        assertThat(relay.backoffFor(1)).isEqualTo(Duration.ofSeconds(2));
        assertThat(relay.backoffFor(3)).isEqualTo(Duration.ofSeconds(8));
        assertThat(relay.backoffFor(10)).isEqualTo(Duration.ofSeconds(60));
    }

    /** Devuelve los resultados programados en orden; por defecto, ACCEPTED. */
    private static final class ScriptedForwarder implements TelemetryForwarder {
        final Deque<ForwardResult> script = new ArrayDeque<>();
        final List<Reading> sent = new ArrayList<>();

        @Override
        public ForwardResult forward(Reading reading) {
            sent.add(reading);
            ForwardResult next = script.pollFirst();
            return next != null ? next : new ForwardResult.Accepted(sent.size() * 1L, "ACCEPTED", 1L, false);
        }
    }
}
