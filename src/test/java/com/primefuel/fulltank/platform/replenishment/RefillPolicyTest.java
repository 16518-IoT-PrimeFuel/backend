package com.primefuel.fulltank.platform.replenishment;

import com.primefuel.fulltank.platform.inventory.application.commandservices.FuelProductCommandService;
import com.primefuel.fulltank.platform.inventory.domain.model.commands.CreateFuelProductCommand;
import com.primefuel.fulltank.platform.inventory.domain.model.valueobjects.FuelType;
import com.primefuel.fulltank.platform.replenishment.application.commandservices.RefillPolicyCommandService;
import com.primefuel.fulltank.platform.replenishment.application.commandservices.ReplenishmentCommandService;
import com.primefuel.fulltank.platform.replenishment.application.queryservices.RefillPolicyQueryService;
import com.primefuel.fulltank.platform.replenishment.domain.model.aggregates.RefillEpisode;
import com.primefuel.fulltank.platform.replenishment.domain.model.commands.ConfigureRefillPolicyCommand;
import com.primefuel.fulltank.platform.replenishment.domain.model.commands.CreateReplenishmentRequestCommand;
import com.primefuel.fulltank.platform.replenishment.domain.model.commands.EvaluateRefillPolicyCommand;
import com.primefuel.fulltank.platform.replenishment.domain.model.commands.RejectReplenishmentRequestCommand;
import com.primefuel.fulltank.platform.replenishment.domain.model.queries.GetRefillEpisodesByTankQuery;
import com.primefuel.fulltank.platform.replenishment.domain.model.valueobjects.RefillDecision;
import com.primefuel.fulltank.platform.replenishment.domain.model.valueobjects.RefillDecisionType;
import com.primefuel.fulltank.platform.replenishment.domain.model.valueobjects.RefillEpisodeStatus;
import com.primefuel.fulltank.platform.replenishment.domain.model.valueobjects.ReplenishmentSource;
import com.primefuel.fulltank.platform.shared.application.result.ApplicationError;
import com.primefuel.fulltank.platform.shared.application.result.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the policy through the application services with a controlled clock (S09/T09-A). It proves
 * the deterministic decisions — threshold boundary, hysteresis, re-arm, pending-request suppression and
 * the "a rejected request never opens a loop" rule — and that all of it stays in shadow mode.
 */
@SpringBootTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:refill_policy;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "authorization.jwt.secret=0123456789abcdef0123456789abcdef"
})
class RefillPolicyTest {

    private static final Instant T0 = Instant.parse("2026-09-22T10:00:00Z");
    private static final double CAPACITY = 500.0;
    private static final MutableClock CLOCK = new MutableClock(T0);

    @TestConfiguration
    static class ClockTestConfiguration {
        @Bean
        @Primary
        Clock testClock() {
            return CLOCK;
        }
    }

    @Autowired
    private RefillPolicyCommandService refillPolicyCommandService;

    @Autowired
    private RefillPolicyQueryService refillPolicyQueryService;

    @Autowired
    private ReplenishmentCommandService replenishmentCommandService;

    @Autowired
    private FuelProductCommandService fuelProductCommandService;

    @BeforeEach
    void resetClock() {
        CLOCK.set(T0);
    }

    private Result<RefillDecision, ApplicationError> evaluate(Long tankId, double level) {
        return refillPolicyCommandService.handle(new EvaluateRefillPolicyCommand(
                tankId, 1L, null, level, "LITRE", CAPACITY, null, null));
    }

    private Optional<RefillEpisode> openEpisode(Long tankId) {
        return refillPolicyQueryService.handle(new GetRefillEpisodesByTankQuery(tankId)).stream()
                .filter(RefillEpisode::isOpen).findFirst();
    }

    private List<RefillEpisode> episodes(Long tankId) {
        return refillPolicyQueryService.handle(new GetRefillEpisodesByTankQuery(tankId));
    }

    private Long aProduct() {
        var product = fuelProductCommandService.handle(new CreateFuelProductCommand(
                "Diesel refill", FuelType.DIESEL, 12.5, "GAL", 1000.0, 1000.0, 7L, true));
        assertThat(product.isSuccess()).isTrue();
        return product.getOrElse(null).getId();
    }

    @Test
    void perTankOverridesOnTopOfTheGlobalDefaults() {
        var configured = refillPolicyCommandService.handle(
                new ConfigureRefillPolicyCommand(301L, 1L, 10.0, 5.0, 80.0, null, null, false));
        assertThat(configured.isSuccess()).isTrue();
        var policy = configured.getOrElse(null);
        assertThat(policy.getLowLevelPercent()).isEqualTo(10.0);
        assertThat(policy.getHysteresisPercent()).isEqualTo(5.0);
        assertThat(policy.getTargetLevelPercent()).isEqualTo(80.0);
        assertThat(policy.getPolicyVersion()).isEqualTo(1);
        assertThat(policy.isAutoGenerateEnabled()).isFalse();

        var reconfigured = refillPolicyCommandService.handle(
                new ConfigureRefillPolicyCommand(301L, 1L, 12.0, 6.0, 90.0, null, null, false));
        assertThat(reconfigured.getOrElse(null).getPolicyVersion()).isEqualTo(2);
    }

    @Test
    void aForeignOrganizationCannotReconfigureATankPolicy() {
        refillPolicyCommandService.handle(
                new ConfigureRefillPolicyCommand(307L, 1L, 10.0, 5.0, 80.0, null, null, false));

        var foreign = refillPolicyCommandService.handle(
                new ConfigureRefillPolicyCommand(307L, 2L, 10.0, 5.0, 80.0, null, null, false));

        assertThat(foreign.isFailure()).isTrue();
    }

    @Test
    void theGlobalDefaultsOpenAnEpisodeAtTwentyPercent() {
        var decision = evaluate(302L, 100.0).getOrElse(null); // 20%

        assertThat(decision.type()).isEqualTo(RefillDecisionType.OPEN_EPISODE);
        assertThat(decision.lowLevelPercent()).isEqualTo(20.0);
        assertThat(decision.rearmPercent()).isEqualTo(30.0);
        assertThat(decision.wouldCreateRequest()).isTrue();

        assertThat(episodes(302L)).hasSize(1);
        var episode = episodes(302L).get(0);
        assertThat(episode.getStatus()).isEqualTo(RefillEpisodeStatus.OPEN);
        assertThat(episode.getOpenedAt()).isEqualTo(T0);
        assertThat(episode.getEpisodeKey()).isEqualTo(decision.episodeKey());
    }

    @Test
    void oneHundredLowReadingsProduceASingleEpisode() {
        for (int i = 0; i < 100; i++) {
            CLOCK.set(T0.plusMillis(i));
            var decision = evaluate(303L, 50.0); // 10%, well below the threshold
            assertThat(decision.isSuccess()).isTrue();
        }

        assertThat(episodes(303L)).hasSize(1);
    }

    @Test
    void anEpisodeOnlyReArmsAfterTheHysteresisRecovery() {
        assertThat(evaluate(304L, 50.0).getOrElse(null).type()).isEqualTo(RefillDecisionType.OPEN_EPISODE);

        // 25% is above the threshold but inside the hysteresis band: still the same open episode.
        CLOCK.set(T0.plusSeconds(1));
        assertThat(evaluate(304L, 125.0).getOrElse(null).type()).isEqualTo(RefillDecisionType.NO_ACTION);
        assertThat(openEpisode(304L)).isPresent();

        // 30% (threshold + hysteresis) re-arms the policy.
        CLOCK.set(T0.plusSeconds(2));
        assertThat(evaluate(304L, 150.0).getOrElse(null).type()).isEqualTo(RefillDecisionType.REARM_EPISODE);
        assertThat(openEpisode(304L)).isEmpty();
        assertThat(episodes(304L)).hasSize(1);
        assertThat(episodes(304L).get(0).getStatus()).isEqualTo(RefillEpisodeStatus.REARMED);

        // A new drop below the threshold opens a fresh episode.
        CLOCK.set(T0.plusSeconds(3));
        assertThat(evaluate(304L, 40.0).getOrElse(null).type()).isEqualTo(RefillDecisionType.OPEN_EPISODE);
        assertThat(episodes(304L)).hasSize(2);
    }

    @Test
    void aPendingRequestSuppressesOpeningANewEpisode() {
        var productId = aProduct();
        var created = replenishmentCommandService.handle(new CreateReplenishmentRequestCommand(
                1L, null, 305L, 7L, productId, 30.0, "GAL", ReplenishmentSource.AUTOMATIC, "manual-305"));
        assertThat(created.isSuccess()).isTrue();

        var decision = evaluate(305L, 50.0).getOrElse(null);

        assertThat(decision.type()).isEqualTo(RefillDecisionType.SUPPRESSED_PENDING_REQUEST);
        assertThat(episodes(305L)).isEmpty();
    }

    @Test
    void aRejectedRequestDoesNotOpenALoop() {
        var productId = aProduct();
        var opened = evaluate(306L, 50.0).getOrElse(null);
        assertThat(opened.type()).isEqualTo(RefillDecisionType.OPEN_EPISODE);

        var created = replenishmentCommandService.handle(new CreateReplenishmentRequestCommand(
                1L, null, 306L, 7L, productId, 50.0, "GAL", ReplenishmentSource.AUTOMATIC, opened.episodeKey()));
        assertThat(created.isSuccess()).isTrue();
        assertThat(replenishmentCommandService.handle(new RejectReplenishmentRequestCommand(
                created.getOrElse(null).getId(), "no stock")).isSuccess()).isTrue();

        // The episode stays open; the rejection cannot re-trigger another request until recovery.
        CLOCK.set(T0.plusSeconds(1));
        assertThat(evaluate(306L, 50.0).getOrElse(null).type()).isEqualTo(RefillDecisionType.NO_ACTION);
        assertThat(episodes(306L)).hasSize(1);
    }

    @Test
    void automationIsOptInPerTank() {
        var shadow = refillPolicyCommandService.handle(
                new ConfigureRefillPolicyCommand(308L, 1L, null, null, null, 7L, 1L, false)).getOrElse(null);
        assertThat(shadow.canGenerateRequests()).isFalse();

        var optedIn = refillPolicyCommandService.handle(
                new ConfigureRefillPolicyCommand(308L, 1L, null, null, null, 7L, 1L, true)).getOrElse(null);
        assertThat(optedIn.isAutoGenerateEnabled()).isTrue();
        assertThat(optedIn.canGenerateRequests()).isTrue();
    }

    static final class MutableClock extends Clock {

        private Instant instant;

        MutableClock(Instant initial) {
            this.instant = initial;
        }

        void set(Instant instant) {
            this.instant = instant;
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
            return instant;
        }
    }
}
