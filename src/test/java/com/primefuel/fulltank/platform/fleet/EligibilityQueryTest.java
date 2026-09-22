package com.primefuel.fulltank.platform.fleet;

import com.primefuel.fulltank.platform.fleet.api.EligibilityQuery;
import com.primefuel.fulltank.platform.fleet.api.FleetCatalog;
import com.primefuel.fulltank.platform.fleet.api.FleetRegistry;
import com.primefuel.fulltank.platform.fleet.domain.model.commands.RegisterDriverCommand;
import com.primefuel.fulltank.platform.fleet.domain.model.commands.RegisterTankerCommand;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T12-B / U07: eligibility is status + lifecycle flag + tenant — value-based, no expiry date. The
 * three-valued outcome keeps "busy" (assigned/in route) apart from "ineligible".
 */
@SpringBootTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:fleet_eligibility;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "authorization.jwt.secret=0123456789abcdef0123456789abcdef"
})
class EligibilityQueryTest {

    @Autowired
    private FleetRegistry fleetRegistry;

    @Autowired
    private EligibilityQuery eligibilityQuery;

    private long driver(Long providerId, String licenseNumber, String status) {
        var result = fleetRegistry.registerDriver(new RegisterDriverCommand(
                providerId, null, "Elig", "Driver", licenseNumber, "999000111", "elig@example.test", status));
        assertThat(result.isSuccess()).isTrue();
        return result.getOrElse(null).id();
    }

    private long tanker(Long providerId, String licensePlate, String status) {
        var result = fleetRegistry.registerTanker(new RegisterTankerCommand(
                providerId, licensePlate, "Volvo", "FH", 2000.0, "GALLONS", status));
        assertThat(result.isSuccess()).isTrue();
        return result.getOrElse(null).id();
    }

    @Test
    void availableDriverIsEligible() {
        var id = driver(1L, "EQ-100", "AVAILABLE");

        var assessment = eligibilityQuery.assessDriver(1L, id);

        assertThat(assessment).isPresent();
        assertThat(assessment.orElseThrow().outcome()).isEqualTo(EligibilityQuery.Outcome.ELIGIBLE);
    }

    @Test
    void assignedDriverIsBusyNotIneligible() {
        var id = driver(1L, "EQ-101", "ASSIGNED");

        var assessment = eligibilityQuery.assessDriver(1L, id).orElseThrow();

        assertThat(assessment.outcome()).isEqualTo(EligibilityQuery.Outcome.BUSY);
        assertThat(eligibilityQuery.eligibleDrivers(1L))
                .extracting(FleetCatalog.DriverSnapshot::id).doesNotContain(id);
    }

    @Test
    void suspendedDriverIsIneligible() {
        var id = driver(1L, "EQ-102", "SUSPENDED");

        assertThat(eligibilityQuery.assessDriver(1L, id).orElseThrow().outcome())
                .isEqualTo(EligibilityQuery.Outcome.INELIGIBLE);
    }

    @Test
    void deactivatedDriverIsIneligibleEvenWhenAvailable() {
        var id = driver(1L, "EQ-103", "AVAILABLE");
        assertThat(eligibilityQuery.assessDriver(1L, id).orElseThrow().outcome())
                .isEqualTo(EligibilityQuery.Outcome.ELIGIBLE);

        fleetRegistry.deactivateDriver(id);

        assertThat(eligibilityQuery.assessDriver(1L, id).orElseThrow().outcome())
                .isEqualTo(EligibilityQuery.Outcome.INELIGIBLE);
        assertThat(eligibilityQuery.eligibleDrivers(1L))
                .extracting(FleetCatalog.DriverSnapshot::id).doesNotContain(id);
    }

    @Test
    void tankerEligibilityFollowsTheSameRules() {
        var available = tanker(1L, "EQ-T01", "AVAILABLE");
        var inRoute = tanker(1L, "EQ-T02", "IN_ROUTE");
        var maintenance = tanker(1L, "EQ-T03", "MAINTENANCE");

        assertThat(eligibilityQuery.assessTanker(1L, available).orElseThrow().outcome())
                .isEqualTo(EligibilityQuery.Outcome.ELIGIBLE);
        assertThat(eligibilityQuery.assessTanker(1L, inRoute).orElseThrow().outcome())
                .isEqualTo(EligibilityQuery.Outcome.BUSY);
        assertThat(eligibilityQuery.assessTanker(1L, maintenance).orElseThrow().outcome())
                .isEqualTo(EligibilityQuery.Outcome.INELIGIBLE);
        assertThat(eligibilityQuery.eligibleTankers(1L))
                .extracting(FleetCatalog.TankerSnapshot::id).containsExactly(available);
    }

    @Test
    void aForeignResourceIsNotAssessable() {
        var id = driver(7L, "EQ-104", "AVAILABLE");

        assertThat(eligibilityQuery.assessDriver(8L, id)).isEmpty();
        assertThat(eligibilityQuery.eligibleDrivers(8L)).isEmpty();
    }
}
