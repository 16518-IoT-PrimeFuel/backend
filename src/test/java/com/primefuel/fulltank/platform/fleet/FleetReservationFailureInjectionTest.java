package com.primefuel.fulltank.platform.fleet;

import com.primefuel.fulltank.platform.fleet.api.FleetRegistry;
import com.primefuel.fulltank.platform.fleet.api.FleetReservations;
import com.primefuel.fulltank.platform.fleet.domain.model.commands.RegisterDriverCommand;
import com.primefuel.fulltank.platform.fleet.domain.model.commands.RegisterTankerCommand;
import com.primefuel.fulltank.platform.fleet.domain.model.commands.ReserveFleetCommand;
import com.primefuel.fulltank.platform.fleet.domain.repositories.FleetReservationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

/**
 * T13-B failure injection: a fault raised <em>after</em> the driver and tanker rows are locked must roll the
 * transaction back — no reservation row survives and, crucially, the row locks are released so the same
 * resource can be reserved immediately afterwards. The save is failed on the first call and delegated to
 * the real adapter afterwards.
 */
@SpringBootTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:fleet_failure_injection;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "authorization.jwt.secret=0123456789abcdef0123456789abcdef"
})
class FleetReservationFailureInjectionTest {

    private static final Instant T0 = Instant.parse("2026-10-01T08:00:00Z");
    private static final Instant T1 = Instant.parse("2026-10-01T09:00:00Z");

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Autowired
    private FleetRegistry fleetRegistry;

    @Autowired
    private FleetReservations fleetReservations;

    @MockitoSpyBean
    private FleetReservationRepository reservationRepository;

    private long driver(long providerId) {
        int sequence = SEQUENCE.incrementAndGet();
        var result = fleetRegistry.registerDriver(new RegisterDriverCommand(
                providerId, null, "Inject", "Driver", "L-INJ-" + sequence, "999000222",
                "inj-driver-" + sequence + "@example.test", "AVAILABLE"));
        assertThat(result.isSuccess()).isTrue();
        return result.getOrElse(null).id();
    }

    private long tanker(long providerId) {
        int sequence = SEQUENCE.incrementAndGet();
        var result = fleetRegistry.registerTanker(new RegisterTankerCommand(
                providerId, "INJ-" + sequence, "Volvo", "FH", 1000.0, "LITRE", "AVAILABLE"));
        assertThat(result.isSuccess()).isTrue();
        return result.getOrElse(null).id();
    }

    @Test
    void aFailureAfterTheResourcesAreLockedLeavesNoHoldAndReleasesTheLocks() {
        long providerId = 1L;
        long driverId = driver(providerId);
        long tankerId = tanker(providerId);

        var failNextSave = new AtomicBoolean(true);
        doAnswer(invocation -> {
            if (failNextSave.getAndSet(false)) {
                throw new IllegalStateException("injected failure after the resource lock");
            }
            return invocation.callRealMethod();
        }).when(reservationRepository).save(any());

        var command = new ReserveFleetCommand(providerId, driverId, tankerId, "inject-1", T0, T1, 100.0, "LITRE");
        // Spring's persistence exception translation wraps the injected fault; the cause is still ours.
        assertThatThrownBy(() -> fleetReservations.reserve(command))
                .hasMessageContaining("injected")
                .hasRootCauseInstanceOf(IllegalStateException.class)
                .hasRootCauseMessage("injected failure after the resource lock");

        // The rollback left no reservation behind for the failed reference.
        assertThat(reservationRepository.findByReference("inject-1")).isEmpty();

        // The driver+tanker locks were released with the rolled-back transaction: reserving the very same
        // resource immediately must succeed rather than block on a leaked lock.
        var afterFailure = fleetReservations.reserve(
                new ReserveFleetCommand(providerId, driverId, tankerId, "inject-2", T0, T1, 100.0, "LITRE"));
        assertThat(afterFailure.isSuccess()).isTrue();
    }
}
