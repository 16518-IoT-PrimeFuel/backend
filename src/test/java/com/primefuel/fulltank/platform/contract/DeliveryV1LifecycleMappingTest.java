package com.primefuel.fulltank.platform.contract;

import com.primefuel.fulltank.platform.fulfillment.domain.model.aggregates.Delivery;
import com.primefuel.fulltank.platform.fulfillment.domain.model.entities.DeliveryStateTransition;
import com.primefuel.fulltank.platform.fulfillment.domain.model.valueobjects.DeliveryPhysicalState;
import com.primefuel.fulltank.platform.fulfillment.domain.repositories.DeliveryRepository;
import com.primefuel.fulltank.platform.fulfillment.domain.repositories.DeliveryStateTransitionRepository;
import com.primefuel.fulltank.platform.iam.infrastructure.authorization.sfs.model.UserDetailsImpl;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T14-B golden contract for the v1 delivery endpoints rerouted through the physical machine. It keeps the
 * legacy HTTP contract (routes, bodies, {@code status}) while proving the state is written in the physical
 * lifecycle + journal: dispatch → ASSIGNED, complete → COMPLETED (with the order's requested quantity as the
 * delivered evidence), fail → FAILED, and a repeated complete does not double-count.
 */
@SpringBootTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:delivery_v1_mapping;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "authorization.jwt.secret=0123456789abcdef0123456789abcdef"
})
@AutoConfigureMockMvc
class DeliveryV1LifecycleMappingTest {

    private static final AtomicInteger RUC_SEQUENCE = new AtomicInteger();
    private static final AtomicInteger USER_ID_SEQUENCE = new AtomicInteger(8_000);
    private static final AtomicInteger FIXTURE_SEQUENCE = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DeliveryRepository deliveryRepository;

    @Autowired
    private DeliveryStateTransitionRepository transitionRepository;

    @MockitoBean
    private JavaMailSender mailSender;

    @Test
    void dispatchAndCompleteWriteThePhysicalLifecycleAndJournal() throws Exception {
        var f = new Fixture("v1-mapping");
        long deliveryId = f.createDelivery(f.createDirectOrder());

        // create: legacy DISPATCHED, no physical_state written (derived ASSIGNED).
        assertThat(deliveryRepository.findById(deliveryId).orElseThrow().getPhysicalState()).isNull();
        assertThat(deliveryRepository.findById(deliveryId).orElseThrow().currentPhysicalState())
                .isEqualTo(DeliveryPhysicalState.ASSIGNED);

        mockMvc.perform(post("/api/v1/deliveries/{id}/dispatch", deliveryId).with(f.provider))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISPATCHED"));
        assertThat(deliveryRepository.findById(deliveryId).orElseThrow().getPhysicalState())
                .isEqualTo(DeliveryPhysicalState.ASSIGNED);
        assertThat(transitionRepository.findByDeliveryId(deliveryId))
                .extracting(DeliveryStateTransition::toState)
                .containsExactly(DeliveryPhysicalState.ASSIGNED);

        mockMvc.perform(post("/api/v1/deliveries/{id}/complete", deliveryId).with(f.provider))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED"));

        var completed = deliveryRepository.findById(deliveryId).orElseThrow();
        assertThat(completed.currentPhysicalState()).isEqualTo(DeliveryPhysicalState.COMPLETED);
        // v1 has no volume: the order's requested quantity (5.0) is the delivered evidence (A2).
        assertThat(completed.getRequestedVolume()).isEqualTo(5.0);
        assertThat(completed.getDeliveredVolume()).isEqualTo(5.0);
        // v1 never recorded the intermediate states; the adapter materialised them before closing (A1).
        assertThat(transitionRepository.findByDeliveryId(deliveryId))
                .extracting(DeliveryStateTransition::toState)
                .containsExactly(DeliveryPhysicalState.ASSIGNED, DeliveryPhysicalState.STARTED,
                        DeliveryPhysicalState.ARRIVED, DeliveryPhysicalState.DELIVERING,
                        DeliveryPhysicalState.COMPLETED);
    }

    @Test
    void aRepeatedCompleteIsRejectedAndDoesNotDoubleCount() throws Exception {
        var f = new Fixture("v1-mapping-retry");
        long deliveryId = f.createDelivery(f.createDirectOrder());
        mockMvc.perform(post("/api/v1/deliveries/{id}/complete", deliveryId).with(f.provider))
                .andExpect(status().isOk());
        var journalSize = transitionRepository.findByDeliveryId(deliveryId).size();

        mockMvc.perform(post("/api/v1/deliveries/{id}/complete", deliveryId).with(f.provider))
                .andExpect(status().isConflict());

        assertThat(deliveryRepository.findById(deliveryId).orElseThrow().getDeliveredVolume()).isEqualTo(5.0);
        assertThat(transitionRepository.findByDeliveryId(deliveryId)).hasSize(journalSize);
    }

    @Test
    void failMovesTheDeliveryToTheTerminalPhysicalState() throws Exception {
        var f = new Fixture("v1-mapping-fail");
        long deliveryId = f.createDelivery(f.createDirectOrder());

        mockMvc.perform(post("/api/v1/deliveries/{id}/fail", deliveryId).with(f.provider)
                        .contentType("application/json")
                        .content("{\"reason\":\"site closed\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"));

        assertThat(deliveryRepository.findById(deliveryId).orElseThrow().currentPhysicalState())
                .isEqualTo(DeliveryPhysicalState.FAILED);
    }

    // ---- fixture -------------------------------------------------------------------------------

    private final class Fixture {
        final RequestPostProcessor buyer;
        final RequestPostProcessor provider;
        final long buyerCompanyId;
        final long providerId;
        final long fuelProductId;

        Fixture(String label) throws Exception {
            this.buyerCompanyId = signUpBuyer(label + "-buyer@example.test");
            this.providerId = signUpProvider(label + "-provider@example.test");
            this.buyer = authFor(buyerCompanyId, null, "ROLE_BUYER");
            this.provider = authFor(null, providerId, "ROLE_PROVIDER");
            this.fuelProductId = createFuelProduct();
        }

        long createDirectOrder() throws Exception {
            var response = mockMvc.perform(post("/api/v1/fuel-orders")
                            .with(buyer)
                            .contentType("application/json")
                            .content("""
                                    {"companyId":%d,"providerId":%d,"fuelProductId":%d,
                                     "requestedQuantity":5,"deliveryAddress":"Av. Mapping 1",
                                     "scheduledDate":"2026-10-15"}
                                    """.formatted(buyerCompanyId, providerId, fuelProductId)))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();
            return objectMapper.readTree(response).get("id").asLong();
        }

        long createDelivery(long orderId) throws Exception {
            long driverId = createDriver();
            long vehicleId = createVehicle();
            var response = mockMvc.perform(post("/api/v1/deliveries")
                            .with(provider)
                            .contentType("application/json")
                            .content("""
                                    {"orderId":%d,"providerId":%d,"driverId":%d,"vehicleId":%d,
                                     "scheduledDate":"2026-10-01","notes":"v1 mapping fixture"}
                                    """.formatted(orderId, providerId, driverId, vehicleId)))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();
            return objectMapper.readTree(response).get("id").asLong();
        }

        private long createFuelProduct() throws Exception {
            var response = mockMvc.perform(post("/api/v1/fuel-products")
                            .with(provider)
                            .contentType("application/json")
                            .content("""
                                    {"name":"Mapping Diesel","fuelType":"DIESEL","pricePerUnit":10.0,"unit":"GALLONS",
                                     "availableStock":500,"capacity":1000,"providerId":%d,"active":true}
                                    """.formatted(providerId)))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();
            return objectMapper.readTree(response).get("id").asLong();
        }

        private long createDriver() throws Exception {
            var response = mockMvc.perform(post("/api/v1/drivers")
                            .with(provider)
                            .contentType("application/json")
                            .content("""
                                    {"providerId":%d,"firstName":"Mapping","lastName":"Driver",
                                     "licenseNumber":"L-MAP-%d","phoneNumber":"999000113","email":"mapping-driver-%d@example.test",
                                     "status":"AVAILABLE"}
                                    """.formatted(providerId, FIXTURE_SEQUENCE.incrementAndGet(), FIXTURE_SEQUENCE.incrementAndGet())))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();
            return objectMapper.readTree(response).get("id").asLong();
        }

        private long createVehicle() throws Exception {
            var response = mockMvc.perform(post("/api/v1/vehicles")
                            .with(provider)
                            .contentType("application/json")
                            .content("""
                                    {"providerId":%d,"licensePlate":"MAP-V%d","brand":"Volvo","model":"FH",
                                     "capacity":2000,"unit":"GALLONS","status":"AVAILABLE"}
                                    """.formatted(providerId, FIXTURE_SEQUENCE.incrementAndGet())))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();
            return objectMapper.readTree(response).get("id").asLong();
        }
    }

    private long signUpBuyer(String username) throws Exception {
        var response = mockMvc.perform(post("/api/v1/authentication/sign-up")
                        .contentType("application/json")
                        .content("""
                                {"username":"%s","password":"StrongPass1!","roles":["ROLE_BUYER"],
                                 "buyerCompany":{"name":"Mapping Buyer LLC","ruc":"%s","sector":"Fuel",
                                 "address":"Lima","contactEmail":"%s","phone":"999111222"}}
                                """.formatted(username, nextRuc(), username)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("companyId").asLong();
    }

    private long signUpProvider(String username) throws Exception {
        var response = mockMvc.perform(post("/api/v1/authentication/sign-up")
                        .contentType("application/json")
                        .content("""
                                {"username":"%s","password":"StrongPass1!","roles":["ROLE_PROVIDER"],
                                 "providerCompany":{"name":"Mapping Provider SAC","ruc":"%s",
                                 "address":"Lima","phone":"999111223","fuelTypesOffered":["DIESEL"]}}
                                """.formatted(username, nextRuc())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("providerId").asLong();
    }

    private static String nextRuc() {
        return "219%08d".formatted(RUC_SEQUENCE.incrementAndGet());
    }

    private static RequestPostProcessor authFor(Long companyId, Long providerId, String role) {
        long userId = USER_ID_SEQUENCE.incrementAndGet();
        var principal = new UserDetailsImpl(userId, "mapping-user-" + userId, "encoded", companyId,
                providerId, List.of(new SimpleGrantedAuthority(role)));
        var token = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        return SecurityMockMvcRequestPostProcessors.authentication(token);
    }
}
