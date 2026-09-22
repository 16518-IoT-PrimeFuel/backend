package com.primefuel.fulltank.platform.fleet;

import com.primefuel.fulltank.platform.iam.infrastructure.authorization.sfs.model.UserDetailsImpl;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
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

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T12-A (review fix): exercises the fleet v2 controllers end to end over HTTP — the tenancy boundary and
 * a real disable/enable round trip, plus the T12-B eligibility endpoints. The service-level behaviour is
 * covered by {@link FleetRegistryTest} and {@link EligibilityQueryTest}.
 */
@SpringBootTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:fleet_rest;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "authorization.jwt.secret=0123456789abcdef0123456789abcdef"
})
@AutoConfigureMockMvc
class FleetV2ControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private JavaMailSender mailSender;

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Test
    void aDriverCanBeDisabledAndReenabledOverHttpAndTheRowSurvives() throws Exception {
        long providerId = signUpProvider();
        var provider = authFor(providerId);
        long driverId = createDriver(provider, "REST-1", "AVAILABLE");

        mockMvc.perform(post("/api/v2/drivers/{id}/deactivate", driverId).with(provider))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        // Soft-disable: the resource is still readable, it is just not active.
        mockMvc.perform(get("/api/v2/drivers/{id}", driverId).with(provider))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(driverId))
                .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(post("/api/v2/drivers/{id}/activate", driverId).with(provider))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void anotherTenantCannotReadOrMutateADriver() throws Exception {
        long ownerProviderId = signUpProvider();
        long otherProviderId = signUpProvider();
        var owner = authFor(ownerProviderId);
        var other = authFor(otherProviderId);
        long driverId = createDriver(owner, "REST-2", "AVAILABLE");

        mockMvc.perform(get("/api/v2/drivers/{id}", driverId).with(other))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v2/drivers/{id}/deactivate", driverId).with(other))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v2/drivers/{id}/activate", driverId).with(other))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v2/drivers/{id}/eligibility", driverId).with(other))
                .andExpect(status().isNotFound());
        // The other tenant's own listing never leaks the driver.
        mockMvc.perform(get("/api/v2/drivers").with(other))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == %d)]".formatted(driverId)).isEmpty());
    }

    @Test
    void aTankerCanBeDisabledAndReenabledOverHttp() throws Exception {
        long providerId = signUpProvider();
        var provider = authFor(providerId);
        long tankerId = createTanker(provider, "REST-T1", "AVAILABLE");

        mockMvc.perform(post("/api/v2/tankers/{id}/deactivate", tankerId).with(provider))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
        mockMvc.perform(post("/api/v2/tankers/{id}/activate", tankerId).with(provider))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void eligibilityIsReportedOverHttpAndRespectsTheTenant() throws Exception {
        long providerId = signUpProvider();
        long otherProviderId = signUpProvider();
        var provider = authFor(providerId);
        var other = authFor(otherProviderId);
        long availableId = createDriver(provider, "REST-3", "AVAILABLE");
        long busyId = createDriver(provider, "REST-4", "ASSIGNED");
        long suspendedId = createDriver(provider, "REST-5", "SUSPENDED");

        mockMvc.perform(get("/api/v2/drivers/{id}/eligibility", availableId).with(provider))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("ELIGIBLE"));
        mockMvc.perform(get("/api/v2/drivers/{id}/eligibility", busyId).with(provider))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("BUSY"));
        mockMvc.perform(get("/api/v2/drivers/{id}/eligibility", suspendedId).with(provider))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("INELIGIBLE"));
        mockMvc.perform(get("/api/v2/drivers/eligible").with(provider))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == %d)]".formatted(availableId)).isNotEmpty())
                .andExpect(jsonPath("$[?(@.id == %d)]".formatted(busyId)).isEmpty())
                .andExpect(jsonPath("$[?(@.id == %d)]".formatted(suspendedId)).isEmpty());
        // A foreign tenant gets a 404 rather than a cross-tenant answer.
        mockMvc.perform(get("/api/v2/drivers/{id}/eligibility", availableId).with(other))
                .andExpect(status().isNotFound());
    }

    // ---- fixtures ---------------------------------------------------------------------------

    private long signUpProvider() throws Exception {
        int sequence = SEQUENCE.incrementAndGet();
        var response = mockMvc.perform(post("/api/v1/authentication/sign-up")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"fleet-rest-%d@example.test","password":"StrongPass1!",
                                 "roles":["ROLE_PROVIDER"],
                                 "providerCompany":{"name":"Fleet REST SAC","ruc":"209%08d",
                                 "address":"Lima","phone":"999111224","fuelTypesOffered":["DIESEL"]}}
                                """.formatted(sequence, sequence)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("providerId").asLong();
    }

    private long createDriver(RequestPostProcessor provider, String licenseNumber, String status)
            throws Exception {
        var response = mockMvc.perform(post("/api/v2/drivers")
                        .with(provider)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Fleet","lastName":"Driver","licenseNumber":"%s",
                                 "phoneNumber":"999000111","email":"fleet-driver@example.test","status":"%s"}
                                """.formatted(licenseNumber, status)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode body = objectMapper.readTree(response);
        return body.get("id").asLong();
    }

    private long createTanker(RequestPostProcessor provider, String licensePlate, String status)
            throws Exception {
        var response = mockMvc.perform(post("/api/v2/tankers")
                        .with(provider)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"licensePlate":"%s","brand":"Volvo","model":"FH","capacity":2000,
                                 "unit":"GALLONS","status":"%s"}
                                """.formatted(licensePlate, status)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private static RequestPostProcessor authFor(long providerId) {
        var principal = new UserDetailsImpl(providerId, "provider-" + providerId, "encoded", null, providerId,
                List.of(new SimpleGrantedAuthority("ROLE_PROVIDER")));
        var token = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        return SecurityMockMvcRequestPostProcessors.authentication(token);
    }
}
