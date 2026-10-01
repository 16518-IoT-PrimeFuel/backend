package com.primefuel.fulltank.platform.replenishment;

import com.primefuel.fulltank.platform.iam.api.MembershipAccess;
import com.primefuel.fulltank.platform.iam.api.TenantAccess;
import com.primefuel.fulltank.platform.replenishment.domain.model.aggregates.ReplenishmentRequest;
import com.primefuel.fulltank.platform.replenishment.domain.model.valueobjects.ReplenishmentSource;
import com.primefuel.fulltank.platform.replenishment.domain.model.valueobjects.ReplenishmentStatus;
import com.primefuel.fulltank.platform.replenishment.domain.repositories.ReplenishmentRequestRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.Optional;
import java.time.LocalDate;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.profiles.active=test", "spring.datasource.url=jdbc:h2:mem:inbox;DB_CLOSE_DELAY=-1", "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=create-drop", "authorization.jwt.secret=0123456789abcdef0123456789abcdef"})
@AutoConfigureMockMvc
class ReplenishmentInboxIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ReplenishmentRequestRepository repository;
    @MockitoBean TenantAccess tenantAccess;
    @MockitoBean MembershipAccess membershipAccess;
    @MockitoBean JavaMailSender mail;

    @Test void providerInboxIncludesEveryClientAndStatusButExcludesOtherProviders() throws Exception {
        var first = save(101L, 901L, ReplenishmentStatus.PENDING);
        var second = save(202L, 901L, ReplenishmentStatus.REJECTED);
        save(303L, 902L, ReplenishmentStatus.PENDING);
        when(tenantAccess.currentProviderId()).thenReturn(Optional.of(901L));
        mvc.perform(get("/api/replenishment-requests/inbox").with(user("provider").roles("PROVIDER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(second.getId()))
                .andExpect(jsonPath("$[1].id").value(first.getId()))
                .andExpect(jsonPath("$[0].organizationId").value(202))
                .andExpect(jsonPath("$[1].organizationId").value(101));
    }

    @Test void buyerCannotReadProviderInbox() throws Exception {
        when(tenantAccess.currentProviderId()).thenReturn(Optional.empty());
        mvc.perform(get("/api/replenishment-requests/inbox").with(user("buyer").roles("BUYER"))).andExpect(status().isForbidden());
    }

    @Test void buyerListRemainsScopedToActiveOrganization() throws Exception {
        var own = save(404L, 903L, ReplenishmentStatus.PENDING);
        save(505L, 903L, ReplenishmentStatus.PENDING);
        when(membershipAccess.currentOrganizationId()).thenReturn(Optional.of(404L));
        mvc.perform(get("/api/replenishment-requests").with(user("buyer").roles("BUYER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(own.getId()));
    }

    private ReplenishmentRequest save(Long organization, Long provider, ReplenishmentStatus status) {
        var request = new ReplenishmentRequest();
        request.setOrganizationId(organization); request.setCustomerAccountId(organization);
        request.setProviderId(provider); request.setFuelProductId(1L); request.setQuantity(100);
        request.setUnit("LITRE"); request.setUnitPrice(4.25); request.setStatus(status);
        request.setSource(ReplenishmentSource.MANUAL); request.setDeliveryAddress("Lima");
        request.setDeliveryDate(LocalDate.now().plusDays(1));
        return repository.save(request);
    }
}
