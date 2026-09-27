package com.primefuel.fulltank.platform.replenishment;

import com.primefuel.fulltank.platform.equipment.application.commandservices.CustomerCommandService;
import com.primefuel.fulltank.platform.equipment.application.commandservices.EquipmentCommandService;
import com.primefuel.fulltank.platform.equipment.application.commandservices.TankCommandService;
import com.primefuel.fulltank.platform.equipment.domain.model.commands.CreateEquipmentCommand;
import com.primefuel.fulltank.platform.equipment.domain.model.commands.RegisterCustomerCommand;
import com.primefuel.fulltank.platform.equipment.domain.model.commands.RegisterSiteCommand;
import com.primefuel.fulltank.platform.equipment.domain.model.commands.RegisterTankCommand;
import com.primefuel.fulltank.platform.equipment.domain.model.valueobjects.EquipmentType;
import com.primefuel.fulltank.platform.fleet.api.FleetRegistry;
import com.primefuel.fulltank.platform.fulfillment.domain.repositories.DeliveryRepository;
import com.primefuel.fulltank.platform.fleet.domain.model.commands.RegisterDriverCommand;
import com.primefuel.fulltank.platform.fleet.domain.model.commands.RegisterTankerCommand;
import com.primefuel.fulltank.platform.iam.infrastructure.authorization.sfs.model.UserDetailsImpl;
import com.primefuel.fulltank.platform.iam.api.MembershipAccess;
import com.primefuel.fulltank.platform.inventory.application.commandservices.FuelProductCommandService;
import com.primefuel.fulltank.platform.inventory.domain.model.commands.CreateFuelProductCommand;
import com.primefuel.fulltank.platform.inventory.domain.model.valueobjects.FuelType;
import com.primefuel.fulltank.platform.ordering.domain.repositories.FuelOrderRepository;
import com.primefuel.fulltank.platform.replenishment.application.commandservices.ReplenishmentCommandService;
import com.primefuel.fulltank.platform.replenishment.domain.model.aggregates.ReplenishmentRequest;
import com.primefuel.fulltank.platform.replenishment.api.ReplenishmentLookup;
import com.primefuel.fulltank.platform.replenishment.domain.model.commands.CreateReplenishmentRequestCommand;
import com.primefuel.fulltank.platform.replenishment.domain.repositories.ReplenishmentRequestRepository;
import com.primefuel.fulltank.platform.supply.api.SupplyReservations;
import com.primefuel.fulltank.platform.supply.domain.model.commands.ReserveSupplyCommand;
import com.primefuel.fulltank.platform.replenishment.domain.model.valueobjects.ReplenishmentSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:replenishment_acceptance;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "authorization.jwt.secret=0123456789abcdef0123456789abcdef"
})
@AutoConfigureMockMvc
class ReplenishmentAcceptanceIntegrationTest {

    private static final AtomicLong IDS = new AtomicLong(7000);

    @Autowired MockMvc mockMvc;
    @Autowired ReplenishmentCommandService replenishmentCommands;
    @Autowired ReplenishmentLookup replenishmentLookup;
    @Autowired ReplenishmentRequestRepository replenishmentRepository;
    @Autowired DeliveryRepository deliveries;
    @Autowired SupplyReservations supplyReservations;
    @Autowired FuelOrderRepository orders;
    @Autowired CustomerCommandService customers;
    @Autowired EquipmentCommandService equipment;
    @Autowired TankCommandService tanks;
    @Autowired FuelProductCommandService products;
    @Autowired FleetRegistry fleet;

    @MockitoBean JavaMailSender mailSender;
    @MockitoBean MembershipAccess membershipAccess;

    @Test
    void acceptanceCreatesOneLinkedOrderAndAllowsV2Delivery() throws Exception {
        var fixture = fixture();
        var created = createRequest(fixture);
        var provider = auth(fixture.providerId(), "ROLE_PROVIDER");

        var acceptedJson = mockMvc.perform(post("/api/replenishment-requests/{id}/accept", created)
                        .with(provider))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.orderId").isNumber())
                .andReturn().getResponse().getContentAsString();
        var accepted = new tools.jackson.databind.ObjectMapper().readTree(acceptedJson);
        var orderId = accepted.get("orderId").asLong();

        assertThat(orders.findByProviderId(fixture.providerId())).hasSize(1);
        assertThat(orders.findById(orderId).orElseThrow().getRequestId()).isNull();
        assertThat(replenishmentLookup.findByOrderId(orderId)).isPresent();

        var driver = fleet.registerDriver(new RegisterDriverCommand(fixture.providerId(), null, "Prueba", "Conductor",
                "L-" + IDS.incrementAndGet(), "999000555", "driver" + IDS.get() + "@example.test", "AVAILABLE"))
                .getOrElse(null);
        var tanker = fleet.registerTanker(new RegisterTankerCommand(fixture.providerId(),
                "T-" + IDS.incrementAndGet(), "Volvo", "FH", 1000.0, "LITRE", "AVAILABLE"))
                .getOrElse(null);

        mockMvc.perform(post("/api/deliveries")
                        .with(provider)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"commandId":"acceptance-%d","orderId":%d,"driverId":%d,"tankerId":%d,
                                 "windowStart":"2026-09-27T12:00:00Z","windowEnd":"2026-09-27T14:00:00Z",
                                 "scheduledDate":"2026-09-27","notes":"prueba de aceptación v2"}
                                """.formatted(IDS.incrementAndGet(), orderId, driver.id(), tanker.id())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").value(orderId));

        mockMvc.perform(post("/api/replenishment-requests/{id}/accept", created)
                        .with(provider))
                .andExpect(status().isConflict());
        assertThat(orders.findByProviderId(fixture.providerId())).hasSize(1);
    }

    @Test
    void differentCommandsCannotCreateTwoDeliveriesForOneOrder() throws Exception {
        var fixture = fixture();
        var requestId = createRequest(fixture);
        var provider = auth(fixture.providerId(), "ROLE_PROVIDER");
        var acceptedJson = mockMvc.perform(post("/api/replenishment-requests/{id}/accept", requestId)
                        .with(provider))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var orderId = new tools.jackson.databind.ObjectMapper().readTree(acceptedJson).get("orderId").asLong();
        var warmup = "supply-warmup-" + IDS.incrementAndGet();
        assertThat(supplyReservations.reserve(new ReserveSupplyCommand(fixture.providerId(), fixture.productId(),
                warmup, 1.0, "LITRE")).isSuccess()).isTrue();
        assertThat(supplyReservations.release(warmup).isSuccess()).isTrue();
        var firstDriver = fleet.registerDriver(new RegisterDriverCommand(fixture.providerId(), null, "Uno", "Driver",
                "L-" + IDS.incrementAndGet(), "999000555", "one" + IDS.get() + "@example.test", "AVAILABLE"))
                .getOrElse(null);
        var secondDriver = fleet.registerDriver(new RegisterDriverCommand(fixture.providerId(), null, "Dos", "Driver",
                "L-" + IDS.incrementAndGet(), "999000556", "two" + IDS.get() + "@example.test", "AVAILABLE"))
                .getOrElse(null);
        var firstTanker = fleet.registerTanker(new RegisterTankerCommand(fixture.providerId(),
                "T-" + IDS.incrementAndGet(), "Volvo", "FH", 1000.0, "LITRE", "AVAILABLE")).getOrElse(null);
        var secondTanker = fleet.registerTanker(new RegisterTankerCommand(fixture.providerId(),
                "T-" + IDS.incrementAndGet(), "Volvo", "FH", 1000.0, "LITRE", "AVAILABLE")).getOrElse(null);
        var gate = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var one = pool.submit(() -> assignAfterGate(gate, provider, orderId, "parallel-one-" + IDS.incrementAndGet(),
                    firstDriver.id(), firstTanker.id()));
            var two = pool.submit(() -> assignAfterGate(gate, provider, orderId, "parallel-two-" + IDS.incrementAndGet(),
                    secondDriver.id(), secondTanker.id()));
            gate.countDown();
            var statuses = List.of(one.get(), two.get()).stream().sorted().toList();
            assertThat(statuses).containsExactly(201, 409);
        }
        var matchingDeliveries = deliveries.findByProviderId(fixture.providerId()).stream()
                .filter(delivery -> delivery.getOrderId() == orderId).count();
        assertThat(matchingDeliveries).isEqualTo(1);
    }

    private int assignAfterGate(CountDownLatch gate, RequestPostProcessor provider, long orderId,
                                String commandId, long driverId, long tankerId) throws Exception {
        gate.await();
        return mockMvc.perform(post("/api/deliveries").with(provider)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"commandId":"%s","orderId":%d,"driverId":%d,"tankerId":%d,
                                 "windowStart":"2026-09-27T12:00:00Z","windowEnd":"2026-09-27T14:00:00Z",
                                 "scheduledDate":"2026-09-27"}
                                """.formatted(commandId, orderId, driverId, tankerId)))
                .andReturn().getResponse().getStatus();
    }

    @Test
    void aTankWithoutLegacyEquipmentIsAcceptedWithAnOrderWithoutEquipment() throws Exception {
        var fixture = fixture(false);
        var requestId = createRequest(fixture);

        mockMvc.perform(post("/api/replenishment-requests/{id}/accept", requestId)
                        .with(auth(fixture.providerId(), "ROLE_PROVIDER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").isNumber());

        assertThat(orders.findByProviderId(fixture.providerId()))
                .singleElement()
                .satisfies(order -> assertThat(order.getEquipmentId()).isNull());
    }

    @Test
    void requestWithoutPersistedDeliveryDetailsCannotBeAccepted() throws Exception {
        var fixture = fixture();
        var request = replenishmentRepository.save(new ReplenishmentRequest(new CreateReplenishmentRequestCommand(
                fixture.organizationId(), fixture.customerId(), fixture.tankId(), fixture.providerId(),
                fixture.productId(), 100.0, "LITRE", ReplenishmentSource.MANUAL, null, null, null), 10.0));

        mockMvc.perform(post("/api/replenishment-requests/{id}/accept", request.getId())
                        .with(auth(fixture.providerId(), "ROLE_PROVIDER")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.details").value("The request has no delivery address or date"));

        var unchanged = replenishmentLookup.findById(request.getId()).orElseThrow();
        assertThat(unchanged.status()).isEqualTo("PENDING");
        assertThat(unchanged.acceptanceConsumed()).isFalse();
        assertThat(orders.findByProviderId(fixture.providerId())).isEmpty();
    }

    @Test
    void creationHidesForeignCustomerAndTankAndKeepsValidCreation() throws Exception {
        var owner = fixture();
        var requester = fixture();
        when(membershipAccess.currentOrganizationId()).thenReturn(java.util.Optional.of(requester.organizationId()));
        var buyer = auth(requester.organizationId(), "ROLE_BUYER");
        var body = """
                {"customerAccountId":%d,"tankId":%d,"providerId":%d,"fuelProductId":%d,
                 "quantity":100,"unit":"LITRE","deliveryAddress":"","deliveryDate":"2099-10-15"}
                """;

        var foreignTank = mockMvc.perform(post("/api/replenishment-requests").with(buyer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.formatted(requester.customerId(), owner.tankId(), requester.providerId(), requester.productId())))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();
        assertThat(foreignTank).doesNotContain(owner.address());

        mockMvc.perform(post("/api/replenishment-requests").with(buyer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.formatted(owner.customerId(), 0L, requester.providerId(), requester.productId())))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/replenishment-requests").with(buyer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.formatted(requester.customerId(), requester.tankId(), requester.providerId(), requester.productId())))
                .andExpect(status().isCreated());
    }

    private Fixture fixture() {
        return fixture(true);
    }

    private Fixture fixture(boolean withLegacyEquipment) {
        var id = IDS.incrementAndGet();
        var organizationId = 80000L + id;
        var legacyCompanyId = 90000L + id;
        var providerId = 100000L + id;
        var customer = customers.handle(new RegisterCustomerCommand(organizationId, "Cliente " + id,
                null, null, null, null, legacyCompanyId)).getOrElse(null);
        var siteAddress = "Av. Prueba " + id;
        var site = customers.handle(new RegisterSiteCommand(organizationId, customer.getId(), "Sitio", siteAddress))
                .getOrElse(null);
        var oldEquipment = equipment.handle(new CreateEquipmentCommand("Equipo " + id, EquipmentType.TRUCK,
                "E-" + id, FuelType.DIESEL, 1000.0, 0.0, "Lima", "AVAILABLE", false,
                10, null, legacyCompanyId, null)).getOrElse(null);
        var tank = tanks.handle(new RegisterTankCommand(organizationId, customer.getId(), site.getId(), "Cisterna " + id,
                "DIESEL", 1000.0, "LITRE", 0.0, withLegacyEquipment ? oldEquipment.getId() : null)).getOrElse(null);
        var product = products.handle(new CreateFuelProductCommand("Diesel " + id, FuelType.DIESEL,
                10.0, "LITRE", 1000.0, 1000.0, providerId, true)).getOrElse(null);
        return new Fixture(organizationId, customer.getId(), tank.getId(), legacyCompanyId,
                oldEquipment.getId(), providerId, product.getId(), siteAddress);
    }

    private Long createRequest(Fixture fixture) {
        var response = replenishmentCommands.handle(new CreateReplenishmentRequestCommand(
                fixture.organizationId(), fixture.customerId(), fixture.tankId(), fixture.providerId(),
                fixture.productId(), 100.0, "LITRE", ReplenishmentSource.MANUAL, null,
                "Av. Prueba 123", LocalDate.of(2026, 9, 27)));
        return response.getOrElse(null).getId();
    }

    private static RequestPostProcessor auth(long id, String role) {
        var providerId = role.equals("ROLE_PROVIDER") ? id : null;
        var companyId = role.equals("ROLE_BUYER") ? id : null;
        var principal = new UserDetailsImpl(id, "user-" + id, "encoded", companyId, providerId,
                List.of(new SimpleGrantedAuthority(role)));
        return SecurityMockMvcRequestPostProcessors.authentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private record Fixture(Long organizationId, Long customerId, Long tankId, Long legacyCompanyId,
                           Long equipmentId, Long providerId, Long productId, String address) {
    }

}
