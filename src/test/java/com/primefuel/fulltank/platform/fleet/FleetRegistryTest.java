package com.primefuel.fulltank.platform.fleet;

import com.primefuel.fulltank.platform.fleet.api.FleetCatalog;
import com.primefuel.fulltank.platform.fleet.api.FleetRegistry;
import com.primefuel.fulltank.platform.fleet.api.events.ResourceDisabledEvent;
import com.primefuel.fulltank.platform.fleet.api.events.ResourceEnabledEvent;
import com.primefuel.fulltank.platform.fleet.domain.model.commands.RegisterDriverCommand;
import com.primefuel.fulltank.platform.fleet.domain.model.commands.RegisterTankerCommand;
import com.primefuel.fulltank.platform.fleet.domain.model.commands.UpdateDriverCommand;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T12-A: the fleet catalog is only reachable through {@code fleet.api}. It proves the tenant invariant,
 * the soft-disable lifecycle (with its events) and that driver identity stays separate from the IAM user.
 */
@SpringBootTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:fleet_registry;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "authorization.jwt.secret=0123456789abcdef0123456789abcdef"
})
@RecordApplicationEvents
class FleetRegistryTest {

    @Autowired
    private FleetRegistry fleetRegistry;

    @Autowired
    private FleetCatalog fleetCatalog;

    @Autowired
    private ApplicationEvents applicationEvents;

    private FleetCatalog.DriverSnapshot registerDriver(Long providerId, String licenseNumber) {
        var result = fleetRegistry.registerDriver(new RegisterDriverCommand(
                providerId, 900L, "Ana", "Lopez", licenseNumber, "999000111", "ana@example.test", "AVAILABLE"));
        assertThat(result.isSuccess()).isTrue();
        return result.getOrElse(null);
    }

    @Test
    void driverIdentityStaysSeparateFromTheIamUser() {
        var driver = registerDriver(1L, "L-100");

        assertThat(driver.id()).isNotNull();
        assertThat(driver.userId()).isEqualTo(900L);
        assertThat(driver.id()).isNotEqualTo(driver.userId());
        assertThat(driver.providerId()).isEqualTo(1L);
        assertThat(driver.status()).isEqualTo("AVAILABLE");
        assertThat(driver.active()).isTrue();
    }

    @Test
    void updateNeverTransfersTheTenant() {
        var driver = registerDriver(1L, "L-101");

        var updated = fleetRegistry.updateDriver(new UpdateDriverCommand(
                driver.id(), "Ana", "Lopez", "L-101", "999000111", "ana@example.test", "SUSPENDED"));

        assertThat(updated.isSuccess()).isTrue();
        assertThat(updated.getOrElse(null).providerId()).isEqualTo(1L);
        assertThat(updated.getOrElse(null).status()).isEqualTo("SUSPENDED");
    }

    @Test
    void deactivatingKeepsTheRowAndPublishesADisabledEvent() {
        var driver = registerDriver(1L, "L-102");

        var disabled = fleetRegistry.deactivateDriver(driver.id());

        assertThat(disabled.getOrElse(null).active()).isFalse();
        // Soft-disable: the row is retained (not deleted), just not active.
        assertThat(fleetCatalog.findDriver(driver.id())).isPresent();
        assertThat(fleetCatalog.findDriver(driver.id()).orElseThrow().active()).isFalse();
        assertThat(applicationEvents.stream(ResourceDisabledEvent.class)).hasSize(1);
    }

    @Test
    void reactivatingPublishesAnEnabledEvent() {
        var driver = registerDriver(1L, "L-103");
        fleetRegistry.deactivateDriver(driver.id());

        var enabled = fleetRegistry.activateDriver(driver.id());

        assertThat(enabled.getOrElse(null).active()).isTrue();
        assertThat(applicationEvents.stream(ResourceEnabledEvent.class)).hasSize(1);
    }

    @Test
    void theCatalogIsScopedToTheTenant() {
        var driver = registerDriver(1L, "L-104");

        assertThat(fleetCatalog.listDrivers(1L)).extracting(FleetCatalog.DriverSnapshot::id).contains(driver.id());
        assertThat(fleetCatalog.listDrivers(2L)).isEmpty();
    }

    @Test
    void registersATankerWithTypedStatus() {
        var result = fleetRegistry.registerTanker(new RegisterTankerCommand(
                1L, "ABC-777", "Volvo", "FH", 2000.0, "GALLONS", "AVAILABLE"));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOrElse(null).status()).isEqualTo("AVAILABLE");
        assertThat(result.getOrElse(null).active()).isTrue();
    }

    @Test
    void rejectsAnUnknownStatus() {
        var result = fleetRegistry.registerDriver(new RegisterDriverCommand(
                1L, null, "Ana", "Lopez", "L-200", "999000111", "ana@example.test", "ON_VACATION"));

        assertThat(result.isFailure()).isTrue();
    }
}
