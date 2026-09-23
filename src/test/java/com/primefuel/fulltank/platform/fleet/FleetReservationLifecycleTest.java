package com.primefuel.fulltank.platform.fleet;

import com.primefuel.fulltank.platform.fleet.domain.model.aggregates.FleetReservation;
import com.primefuel.fulltank.platform.fleet.domain.model.commands.ReserveFleetCommand;
import com.primefuel.fulltank.platform.fleet.domain.model.valueobjects.FleetReservationStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * T13-B unit coverage of the aggregate's terminal transitions: the strict {@code release()} guard and the
 * deterministic, idempotent {@link FleetReservation#expireIfPast(Instant)}. No Spring, no clock: the instant
 * is supplied by the caller exactly as the service does with the injected clock.
 */
class FleetReservationLifecycleTest {

    private static final Instant T0 = Instant.parse("2026-10-01T08:00:00Z");
    private static final Instant T1 = Instant.parse("2026-10-01T09:00:00Z");
    private static final Instant T2 = Instant.parse("2026-10-01T10:00:00Z");

    private static FleetReservation active(Instant start, Instant end) {
        return new FleetReservation(new ReserveFleetCommand(
                1L, 10L, 20L, "ref", start, end, 100.0, "LITRE"));
    }

    @Test
    void expiresOnlyOnceTheWindowHasEnded() {
        var reservation = active(T0, T1);

        assertThat(reservation.expireIfPast(T0)).isFalse();
        assertThat(reservation.getStatus()).isEqualTo(FleetReservationStatus.ACTIVE);

        // half-open [T0, T1): at exactly T1 the window is already over.
        assertThat(reservation.expireIfPast(T1)).isTrue();
        assertThat(reservation.getStatus()).isEqualTo(FleetReservationStatus.EXPIRED);

        // idempotent: a second sweep does not transition it again.
        assertThat(reservation.expireIfPast(T2)).isFalse();
        assertThat(reservation.getStatus()).isEqualTo(FleetReservationStatus.EXPIRED);
    }

    @Test
    void releaseIsTerminalAndGuardedAgainstDoubleRelease() {
        var reservation = active(T0, T1);

        reservation.release();
        assertThat(reservation.getStatus()).isEqualTo(FleetReservationStatus.RELEASED);

        assertThatThrownBy(reservation::release).isInstanceOf(IllegalStateException.class);
        // a released reservation is never swept into EXPIRED.
        assertThat(reservation.expireIfPast(T2)).isFalse();
        assertThat(reservation.getStatus()).isEqualTo(FleetReservationStatus.RELEASED);
    }
}
