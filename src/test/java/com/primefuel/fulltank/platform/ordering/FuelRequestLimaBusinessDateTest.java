package com.primefuel.fulltank.platform.ordering;

import com.primefuel.fulltank.platform.iam.infrastructure.authorization.sfs.model.UserDetailsImpl;
import com.primefuel.fulltank.platform.inventory.domain.model.aggregates.FuelProduct;
import com.primefuel.fulltank.platform.inventory.domain.model.valueobjects.FuelType;
import com.primefuel.fulltank.platform.inventory.domain.repositories.FuelProductRepository;
import com.primefuel.fulltank.platform.ordering.application.internal.commandservices.FuelRequestService;
import com.primefuel.fulltank.platform.ordering.infrastructure.persistence.jpa.repositories.FuelRequestPersistenceRepository;
import com.primefuel.fulltank.platform.ordering.interfaces.rest.resources.CreateFuelRequestResource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:fuel_request_lima_date;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "authorization.jwt.secret=0123456789abcdef0123456789abcdef"
})
@AutoConfigureMockMvc
@Import(FuelRequestLimaBusinessDateTest.FixedClockConfiguration.class)
class FuelRequestLimaBusinessDateTest {
    @Autowired FuelProductRepository products;
    @Autowired FuelRequestService requests;
    @Autowired FuelRequestPersistenceRepository requestRows;
    @Autowired MockMvc mockMvc;
    @MockitoBean JavaMailSender mailSender;

    @Test
    void todayInLimaIsAcceptedAfterUtcMidnightAndYesterdayIsBadRequest() throws Exception {
        var product = new FuelProduct();
        product.setName("Lima Diesel");
        product.setFuelType(FuelType.DIESEL);
        product.setPricePerUnit(10.0);
        product.setUnit("GALLONS");
        product.setAvailableStock(500.0);
        product.setCapacity(1000.0);
        product.setProviderId(42L);
        long productId = products.save(product).getId();

        var today = requests.create(new CreateFuelRequestResource(31L, 42L, null, productId,
                5.0, "GALLONS", "Lima", java.time.LocalDate.parse("2026-09-25"), "MANUAL"));
        assertThat(today.getId()).isNotNull();

        long countBeforePastDate = requestRows.count();
        mockMvc.perform(post("/api/v1/fuel-requests")
                        .with(buyerAuth(31L))
                        .contentType("application/json")
                        .content("""
                                {"buyerCompanyId":31,"providerId":42,"fuelProductId":%d,"quantity":5,
                                 "unit":"GALLONS","deliveryAddress":"Lima","deliveryDate":"2026-09-24",
                                 "source":"MANUAL"}
                                """.formatted(productId)))
                .andExpect(status().isBadRequest());
        assertThat(requestRows.count()).isEqualTo(countBeforePastDate);
    }

    private static RequestPostProcessor buyerAuth(long companyId) {
        var principal = new UserDetailsImpl(9001L, "lima-date-buyer", "encoded", companyId, null,
                List.of(new SimpleGrantedAuthority("ROLE_BUYER")));
        return authentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-09-26T01:00:00Z"), ZoneOffset.UTC);
        }
    }
}
