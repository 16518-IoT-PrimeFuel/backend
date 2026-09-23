package com.primefuel.fulltank.platform.replenishment;

import com.primefuel.fulltank.platform.equipment.api.TankAssets;
import com.primefuel.fulltank.platform.equipment.application.commandservices.CustomerCommandService;
import com.primefuel.fulltank.platform.equipment.application.commandservices.TankCommandService;
import com.primefuel.fulltank.platform.equipment.application.commandservices.TankReadingService;
import com.primefuel.fulltank.platform.equipment.domain.model.commands.RegisterCustomerCommand;
import com.primefuel.fulltank.platform.equipment.domain.model.commands.RegisterTankCommand;
import com.primefuel.fulltank.platform.inventory.application.commandservices.FuelProductCommandService;
import com.primefuel.fulltank.platform.inventory.domain.model.commands.CreateFuelProductCommand;
import com.primefuel.fulltank.platform.inventory.domain.model.valueobjects.FuelType;
import com.primefuel.fulltank.platform.replenishment.application.commandservices.RefillPolicyCommandService;
import com.primefuel.fulltank.platform.replenishment.application.commandservices.ReplenishmentCommandService;
import com.primefuel.fulltank.platform.replenishment.application.internal.consumers.RefillPolicyEvaluationConsumer;
import com.primefuel.fulltank.platform.replenishment.application.queryservices.RefillPolicyQueryService;
import com.primefuel.fulltank.platform.replenishment.domain.model.aggregates.RefillEpisode;
import com.primefuel.fulltank.platform.replenishment.domain.model.aggregates.ReplenishmentRequest;
import com.primefuel.fulltank.platform.replenishment.domain.model.commands.ConfigureRefillPolicyCommand;
import com.primefuel.fulltank.platform.replenishment.domain.model.commands.RejectReplenishmentRequestCommand;
import com.primefuel.fulltank.platform.replenishment.domain.model.queries.GetRefillEpisodesByTankQuery;
import com.primefuel.fulltank.platform.replenishment.domain.model.valueobjects.ReplenishmentSource;
import com.primefuel.fulltank.platform.replenishment.domain.repositories.ReplenishmentRequestRepository;
import com.primefuel.fulltank.platform.telemetry.api.events.ValidatedTankReadingEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end policy generation (S09/T09-B): a hundred low readings produce at most one request, generation
 * is opt-in per tank (shadow by default), re-arming after recovery allows exactly one more, and a rejected
 * request never opens a loop. The consumer is driven directly with a validated-reading event, while the
 * tank snapshot is set through the same equipment seam the telemetry integration uses.
 */
@SpringBootTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:refill_generation;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "authorization.jwt.secret=0123456789abcdef0123456789abcdef"
})
class RefillGenerationIntegrationTest {

    private static final Instant T1 = Instant.now().plusSeconds(120);
    private static final String DEVICE = "dev-refill";
    private static final String CHANNEL = "tank-level";
    private static final long ORGANIZATION = 1L;
    private static final long PROVIDER = 7L;

    @Autowired
    private RefillPolicyEvaluationConsumer consumer;

    @Autowired
    private RefillPolicyCommandService refillPolicyCommandService;

    @Autowired
    private RefillPolicyQueryService refillPolicyQueryService;

    @Autowired
    private ReplenishmentCommandService replenishmentCommandService;

    @Autowired
    private ReplenishmentRequestRepository requestRepository;

    @Autowired
    private TankAssets tankAssets;

    @Autowired
    private CustomerCommandService customerCommandService;

    @Autowired
    private TankCommandService tankCommandService;

    @Autowired
    private TankReadingService tankReadingService;

    @Autowired
    private FuelProductCommandService fuelProductCommandService;

    private Long aTank(String ruc) {
        var customer = customerCommandService.handle(
                new RegisterCustomerCommand(ORGANIZATION, "Cliente refill " + ruc, ruc, null, null, null, null));
        assertThat(customer.isSuccess()).isTrue();
        var tank = tankCommandService.handle(new RegisterTankCommand(
                ORGANIZATION, customer.getOrElse(null).getId(), null, "Tanque refill " + ruc,
                "DIESEL", 500.0, "LITRE", 400.0, null));
        assertThat(tank.isSuccess()).isTrue();
        return tank.getOrElse(null).getId();
    }

    private Long aProduct() {
        var product = fuelProductCommandService.handle(new CreateFuelProductCommand(
                "Diesel refill", FuelType.DIESEL, 12.5, "GAL", 1000.0, 1000.0, PROVIDER, true));
        assertThat(product.isSuccess()).isTrue();
        return product.getOrElse(null).getId();
    }

    private void configure(Long tankId, Long productId, boolean autoGenerate) {
        var policy = refillPolicyCommandService.handle(new ConfigureRefillPolicyCommand(
                tankId, ORGANIZATION, null, null, null, PROVIDER, productId, autoGenerate));
        assertThat(policy.isSuccess()).isTrue();
    }

    private void setLevel(Long tankId, double level, Instant at) {
        assertThat(tankAssets.applyValidatedReading(tankId, level, "LITRE", at)).isTrue();
    }

    private void read(Long tankId, long sequence, Instant at) {
        // Device id includes the tank so the (device, channel, sequence) dedup key in EventInbox
        // can't collide across test methods sharing the same Spring context and H2 schema.
        consumer.on(new ValidatedTankReadingEvent(
                sequence, DEVICE + "-" + tankId, CHANNEL, sequence, tankId, ORGANIZATION, 0.0, "LITRE", at));
    }

    private List<ReplenishmentRequest> requestsFor(Long tankId) {
        return requestRepository.findByOrganizationId(ORGANIZATION).stream()
                .filter(request -> tankId.equals(request.getTankId())).toList();
    }

    private List<RefillEpisode> episodesFor(Long tankId) {
        return refillPolicyQueryService.handle(new GetRefillEpisodesByTankQuery(tankId));
    }

    @Test
    void oneHundredLowReadingsGenerateExactlyOneRequest() {
        var tankId = aTank("20900000001");
        var productId = aProduct();
        configure(tankId, productId, true);
        setLevel(tankId, 50.0, T1); // 10% of 500

        for (long sequence = 1; sequence <= 100; sequence++) {
            read(tankId, sequence, T1.plusSeconds(sequence));
        }

        var requests = requestsFor(tankId);
        assertThat(requests).hasSize(1);
        assertThat(requests.get(0).getSource()).isEqualTo(ReplenishmentSource.AUTOMATIC);
        assertThat(requests.get(0).getQuantity()).isEqualTo(450.0);

        var episodes = episodesFor(tankId);
        assertThat(episodes).hasSize(1);
        assertThat(episodes.get(0).isRequestEmitted()).isTrue();
        assertThat(episodes.get(0).getRequestId()).isEqualTo(requests.get(0).getId());
        assertThat(requests.get(0).getEpisodeKey()).isEqualTo(episodes.get(0).getEpisodeKey());
    }

    @Test
    void shadowModeDoesNotGenerateRequestsByDefault() {
        var tankId = aTank("20900000002");
        var productId = aProduct();
        configure(tankId, productId, false); // explicit opt-out
        setLevel(tankId, 50.0, T1);

        for (long sequence = 1; sequence <= 100; sequence++) {
            read(tankId, sequence, T1.plusSeconds(sequence));
        }

        assertThat(requestsFor(tankId)).isEmpty();
        var episodes = episodesFor(tankId);
        assertThat(episodes).hasSize(1);
        assertThat(episodes.get(0).isRequestEmitted()).isFalse();
    }

    @Test
    void recoveryAndANewCrossingGenerateASecondRequest() {
        var tankId = aTank("20900000003");
        var productId = aProduct();
        configure(tankId, productId, true);

        setLevel(tankId, 50.0, T1);
        read(tankId, 1, T1);
        var first = requestsFor(tankId);
        assertThat(first).hasSize(1);

        // The first intent must be decided before a new episode may open (no active request invariant).
        assertThat(replenishmentCommandService.handle(new RejectReplenishmentRequestCommand(
                first.get(0).getId(), "handled manually")).isSuccess()).isTrue();

        // Recover past threshold + hysteresis (30%): the episode re-arms.
        setLevel(tankId, 160.0, T1.plusSeconds(3600));
        read(tankId, 2, T1.plusSeconds(3600));
        assertThat(episodesFor(tankId).get(0).isOpen()).isFalse();

        // A new drop opens a fresh episode and produces exactly one more request.
        setLevel(tankId, 40.0, T1.plusSeconds(7200));
        read(tankId, 3, T1.plusSeconds(7200));

        assertThat(requestsFor(tankId)).hasSize(2);
        assertThat(episodesFor(tankId)).hasSize(2);
    }

    @Test
    void aRejectedRequestDoesNotOpenALoop() {
        var tankId = aTank("20900000004");
        var productId = aProduct();
        configure(tankId, productId, true);

        setLevel(tankId, 50.0, T1);
        read(tankId, 1, T1);
        var requests = requestsFor(tankId);
        assertThat(requests).hasSize(1);

        assertThat(replenishmentCommandService.handle(new RejectReplenishmentRequestCommand(
                requests.get(0).getId(), "no stock")).isSuccess()).isTrue();

        for (long sequence = 2; sequence <= 10; sequence++) {
            read(tankId, sequence, T1.plusSeconds(sequence));
        }

        assertThat(requestsFor(tankId)).hasSize(1);
        assertThat(episodesFor(tankId)).hasSize(1);
    }

    @Test
    void aReplayedReadingDoesNotDuplicateTheEpisodeOrTheRequest() {
        var tankId = aTank("20900000005");
        var productId = aProduct();
        configure(tankId, productId, true);
        setLevel(tankId, 50.0, T1);

        read(tankId, 1, T1);
        read(tankId, 1, T1); // replay of the same reading identity

        assertThat(requestsFor(tankId)).hasSize(1);
        assertThat(episodesFor(tankId)).hasSize(1);
    }

    @Test
    void aManualLevelEditTriggersTheSameIdempotentEvaluation() {
        var tankId = aTank("20900000006");
        var productId = aProduct();
        configure(tankId, productId, true);

        // The legacy v1 manual level path must trigger the policy exactly like a telemetry reading.
        assertThat(tankReadingService.applyManualLevel(tankId, 50.0, "LITRE").isSuccess()).isTrue();
        assertThat(requestsFor(tankId)).hasSize(1);

        // Further manual edits at (or below) the threshold never produce a second request: the open
        // episode suppresses them, the same guarantee repeated telemetry readings have.
        assertThat(tankReadingService.applyManualLevel(tankId, 45.0, "LITRE").isSuccess()).isTrue();
        assertThat(tankReadingService.applyManualLevel(tankId, 40.0, "LITRE").isSuccess()).isTrue();
        assertThat(requestsFor(tankId)).hasSize(1);
        assertThat(episodesFor(tankId)).hasSize(1);
    }

    @Test
    void aManualLevelEditOnlyRecordsAShadowDecisionWhenAutomationIsOff() {
        var tankId = aTank("20900000007");
        var productId = aProduct();
        configure(tankId, productId, false);

        assertThat(tankReadingService.applyManualLevel(tankId, 50.0, "LITRE").isSuccess()).isTrue();

        assertThat(requestsFor(tankId)).isEmpty();
        var episodes = episodesFor(tankId);
        assertThat(episodes).hasSize(1);
        assertThat(episodes.get(0).isRequestEmitted()).isFalse();
    }
}
