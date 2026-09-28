package com.primefuel.fulltank.platform.replenishment;

import com.primefuel.fulltank.platform.contract.ReplenishmentTestFixtures;
import com.primefuel.fulltank.platform.equipment.application.commandservices.CustomerCommandService;
import com.primefuel.fulltank.platform.equipment.application.commandservices.EquipmentCommandService;
import com.primefuel.fulltank.platform.equipment.application.commandservices.TankCommandService;
import com.primefuel.fulltank.platform.iam.api.MembershipAccess;
import com.primefuel.fulltank.platform.iam.infrastructure.authorization.sfs.model.UserDetailsImpl;
import com.primefuel.fulltank.platform.inventory.application.commandservices.FuelProductCommandService;
import com.primefuel.fulltank.platform.inventory.domain.model.commands.CreateFuelProductCommand;
import com.primefuel.fulltank.platform.inventory.domain.model.valueobjects.FuelType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:replenishment_lima_date;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "authorization.jwt.secret=0123456789abcdef0123456789abcdef"
})
@AutoConfigureMockMvc
class ReplenishmentLimaBusinessDateTest {

    private static final Instant NOW = Instant.parse("2026-09-26T01:00:00Z"); // 25/09, 20:00 en Lima
    private static final long COMPANY_ID = 310L;
    private static final long PROVIDER_ID = 420L;

    @Autowired MockMvc mockMvc;
    @Autowired CustomerCommandService customers;
    @Autowired EquipmentCommandService equipment;
    @Autowired TankCommandService tanks;
    @Autowired FuelProductCommandService products;

    @MockitoBean MembershipAccess membershipAccess;
    @MockitoBean JavaMailSender mailSender;

    @Test
    void rejectsYesterdayInLimaAndAcceptsTodayWithSiteAddressFallback() throws Exception {
        when(membershipAccess.currentOrganizationId()).thenReturn(Optional.of(COMPANY_ID));
        var assets = ReplenishmentTestFixtures.create(customers, equipment, tanks, COMPANY_ID, "Av. T5 Lima 25");
        var product = products.handle(new CreateFuelProductCommand("Diesel T5", FuelType.DIESEL,
                10.0, "LITRE", 1000.0, 1000.0, PROVIDER_ID, true)).getOrElse(null);

        mockMvc.perform(post("/api/replenishment-requests")
                        .with(auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(assets.customerId(), assets.tankId(), product.getId(), "2026-09-24")))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/replenishment-requests")
                        .with(auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(assets.customerId(), assets.tankId(), product.getId(), "2026-09-25")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.deliveryAddress").value("Av. T5 Lima 25"))
                .andExpect(jsonPath("$.deliveryDate").value("2026-09-25"));
    }

    private static String body(Long customerId, Long tankId, Long productId, String deliveryDate) {
        return """
                {"customerAccountId":%d,"tankId":%d,"providerId":%d,"fuelProductId":%d,
                 "quantity":100,"unit":"LITRE","deliveryDate":"%s"}
                """.formatted(customerId, tankId, PROVIDER_ID, productId, deliveryDate);
    }

    private static RequestPostProcessor auth() {
        var principal = new UserDetailsImpl(31L, "t5-buyer", "encoded", COMPANY_ID, null,
                List.of(new SimpleGrantedAuthority("ROLE_BUYER")));
        return SecurityMockMvcRequestPostProcessors.authentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @TestConfiguration
    static class FixedClockConfiguration {
        @Bean
        @Primary
        Clock fixedBusinessClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}
