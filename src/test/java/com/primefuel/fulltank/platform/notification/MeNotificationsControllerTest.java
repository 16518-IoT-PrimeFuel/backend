package com.primefuel.fulltank.platform.notification;

import com.primefuel.fulltank.platform.iam.application.commandservices.MembershipCommandService;
import com.primefuel.fulltank.platform.iam.application.commandservices.OnboardingCommandService;
import com.primefuel.fulltank.platform.iam.domain.model.commands.GrantMembershipCommand;
import com.primefuel.fulltank.platform.iam.domain.model.commands.OnboardOrganizationCommand;
import com.primefuel.fulltank.platform.iam.domain.model.valueobjects.MembershipRole;
import com.primefuel.fulltank.platform.iam.domain.model.valueobjects.OrganizationType;
import com.primefuel.fulltank.platform.iam.infrastructure.authorization.sfs.model.UserDetailsImpl;
import com.primefuel.fulltank.platform.inventory.application.commandservices.FuelProductCommandService;
import com.primefuel.fulltank.platform.inventory.domain.model.commands.CreateFuelProductCommand;
import com.primefuel.fulltank.platform.inventory.domain.model.valueobjects.FuelType;
import com.primefuel.fulltank.platform.notification.domain.repositories.NotificationRepository;
import com.primefuel.fulltank.platform.replenishment.application.commandservices.ReplenishmentCommandService;
import com.primefuel.fulltank.platform.replenishment.domain.model.commands.AcceptReplenishmentRequestCommand;
import com.primefuel.fulltank.platform.replenishment.domain.model.commands.CreateReplenishmentRequestCommand;
import com.primefuel.fulltank.platform.replenishment.domain.model.valueobjects.ReplenishmentSource;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * T20-B: the v2 {@code /api/v2/me/notifications} inbox is private per user, mark-as-read is idempotent, and
 * the deprecated v1 POST keeps working.
 */
@SpringBootTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:me_notifications;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "authorization.jwt.secret=0123456789abcdef0123456789abcdef"
})
@AutoConfigureMockMvc
class MeNotificationsControllerTest {

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OnboardingCommandService onboardingCommandService;

    @Autowired
    private MembershipCommandService membershipCommandService;

    @Autowired
    private ReplenishmentCommandService replenishmentCommandService;

    @Autowired
    private FuelProductCommandService fuelProductCommandService;

    @Autowired
    private NotificationRepository notificationRepository;

    @MockitoBean
    private JavaMailSender mailSender;

    @Test
    void theInboxIsPrivateToEachUserAndMarkAsReadIsIdempotent() throws Exception {
        long ownerUserId = 2001;
        long memberUserId = 2002;
        var organization = onboardingCommandService.handle(new OnboardOrganizationCommand(
                "Me Inbox Org", "MEI" + SEQUENCE.incrementAndGet(), OrganizationType.CUSTOMER, ownerUserId));
        assertThat(organization.isSuccess()).isTrue();
        long organizationId = organization.getOrElse(null).getId();
        assertThat(membershipCommandService.handle(new GrantMembershipCommand(
                organizationId, memberUserId, MembershipRole.MEMBER)).isSuccess()).isTrue();

        long providerId = 10;
        var product = fuelProductCommandService.handle(new CreateFuelProductCommand(
                "Me Inbox Diesel " + SEQUENCE.incrementAndGet(), FuelType.DIESEL, 10.0, "GALLONS", 500.0, 1000.0,
                providerId, true));
        assertThat(product.isSuccess()).isTrue();
        var created = replenishmentCommandService.handle(new CreateReplenishmentRequestCommand(
                organizationId, null, null, providerId, product.getOrElse(null).getId(), 10.0, "GALLONS",
                ReplenishmentSource.MANUAL, "me-inbox-" + SEQUENCE.incrementAndGet()));
        assertThat(created.isSuccess()).isTrue();
        assertThat(replenishmentCommandService.handle(
                new AcceptReplenishmentRequestCommand(created.getOrElse(null).getId())).isSuccess()).isTrue();

        var owner = authFor(ownerUserId);
        var member = authFor(memberUserId);

        // Each user sees only their own notification.
        var ownerList = mockMvc.perform(get("/api/v2/me/notifications").with(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andReturn().getResponse().getContentAsString();
        long ownerNotificationId = objectMapper.readTree(ownerList).get(0).get("id").asLong();

        mockMvc.perform(get("/api/v2/me/notifications").with(member))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].userId").value(memberUserId));

        // One user cannot mark another user's notification as read (privacy).
        mockMvc.perform(post("/api/v2/me/notifications/{id}/read", ownerNotificationId).with(member))
                .andExpect(status().isNotFound());

        // Mark as read, then repeat: idempotent, still exactly one notification.
        mockMvc.perform(post("/api/v2/me/notifications/{id}/read", ownerNotificationId).with(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.read").value(true));
        mockMvc.perform(post("/api/v2/me/notifications/{id}/read", ownerNotificationId).with(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.read").value(true));

        assertThat(notificationRepository.findByUserId(ownerUserId)).hasSize(1);
        mockMvc.perform(get("/api/v2/me/notifications/unread").with(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void theDeprecatedV1PostStillWorks() throws Exception {
        long userId = 2003;
        var user = authFor(userId);
        mockMvc.perform(post("/api/v1/notifications").with(user)
                        .contentType("application/json")
                        .content("""
                                {"userId":%d,"type":"GENERAL","title":"Manual","message":"legacy"}
                                """.formatted(userId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Manual"));
    }

    private static RequestPostProcessor authFor(long userId) {
        var principal = new UserDetailsImpl(userId, "me-" + userId, "encoded", null, null,
                List.of(new SimpleGrantedAuthority("ROLE_BUYER")));
        var token = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        return SecurityMockMvcRequestPostProcessors.authentication(token);
    }
}
