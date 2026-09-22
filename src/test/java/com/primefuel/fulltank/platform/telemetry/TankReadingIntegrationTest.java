package com.primefuel.fulltank.platform.telemetry;

import com.primefuel.fulltank.platform.equipment.application.commandservices.CustomerCommandService;
import com.primefuel.fulltank.platform.equipment.application.commandservices.TankCommandService;
import com.primefuel.fulltank.platform.equipment.domain.model.commands.RegisterCustomerCommand;
import com.primefuel.fulltank.platform.equipment.domain.model.commands.RegisterTankCommand;
import com.primefuel.fulltank.platform.equipment.domain.repositories.TankRepository;
import com.primefuel.fulltank.platform.telemetry.api.events.ValidatedTankReadingEvent;
import com.primefuel.fulltank.platform.telemetry.application.internal.consumers.ValidatedTankReadingConsumer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:tank_reading_integration;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jdbc.auto-commit=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "authorization.jwt.secret=0123456789abcdef0123456789abcdef"
})
class TankReadingIntegrationTest {

    // The tank records its level instant at creation, so readings must be newer than "now" to apply.
    private static final Instant T0 = Instant.now().plusSeconds(60);
    private static final Instant T1 = Instant.now().plusSeconds(3600);
    private static final String DEVICE = "dev-int";
    private static final String CHANNEL = "tank-level";

    @Autowired
    private ValidatedTankReadingConsumer consumer;

    @Autowired
    private CustomerCommandService customerCommandService;

    @Autowired
    private TankCommandService tankCommandService;

    @Autowired
    private TankRepository tankRepository;

    private Long aTank(String ruc) {
        var customer = customerCommandService.handle(
                new RegisterCustomerCommand(1L, "Cliente IoT " + ruc, ruc, null, null, null, null));
        assertThat(customer.isSuccess()).isTrue();
        var tank = tankCommandService.handle(new RegisterTankCommand(
                1L, customer.getOrElse(null).getId(), null, "Tanque IoT " + ruc, "DIESEL", 500.0, "LITRE", 100.0, null));
        assertThat(tank.isSuccess()).isTrue();
        return tank.getOrElse(null).getId();
    }

    private ValidatedTankReadingEvent eventFor(Long tankId, long sequence, double level, Instant capturedAt) {
        return new ValidatedTankReadingEvent(
                sequence, DEVICE, CHANNEL, sequence, tankId, 1L, level, "LITRE", capturedAt);
    }

    @Test
    void appliesReadingsOnceAndNeverRegressesOnReplayOrOutOfOrder() {
        var tankId = aTank("20777777771");

        consumer.on(eventFor(tankId, 1L, 250.0, T1));
        assertThat(tankRepository.findById(tankId).orElseThrow().getCurrentLevel().amount()).isEqualTo(250.0);
        assertThat(tankRepository.findById(tankId).orElseThrow().getLevelSource()).isEqualTo("VALIDATED");

        // Replay of the same event: consumed once, applied once.
        consumer.on(eventFor(tankId, 1L, 250.0, T1));
        assertThat(tankRepository.findById(tankId).orElseThrow().getCurrentLevel().amount()).isEqualTo(250.0);

        // Out-of-order (older) reading: accepted by the inbox, ignored by the tank.
        consumer.on(eventFor(tankId, 2L, 400.0, T0));
        assertThat(tankRepository.findById(tankId).orElseThrow().getCurrentLevel().amount()).isEqualTo(250.0);
        assertThat(tankRepository.findById(tankId).orElseThrow().getLevelObservedAt()).isAfter(T0);

        // A newer reading still moves the snapshot forward.
        consumer.on(eventFor(tankId, 3L, 320.0, T1.plusSeconds(3600)));
        assertThat(tankRepository.findById(tankId).orElseThrow().getCurrentLevel().amount()).isEqualTo(320.0);
    }

    @Test
    void aFailedApplicationIsNotMarkedAsConsumedSoItCanBeRetried() {
        var tankId = aTank("20777777772");

        // A level above the tank capacity must fail and roll the whole consumption back.
        assertThatThrownBy(() -> consumer.on(eventFor(tankId, 10L, 9999.0, T1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(tankRepository.findById(tankId).orElseThrow().getCurrentLevel().amount()).isEqualTo(100.0);

        // The retry path still works, proving the failed consumption left no trace.
        consumer.on(eventFor(tankId, 10L, 200.0, T1));
        assertThat(tankRepository.findById(tankId).orElseThrow().getCurrentLevel().amount()).isEqualTo(200.0);
    }
}
