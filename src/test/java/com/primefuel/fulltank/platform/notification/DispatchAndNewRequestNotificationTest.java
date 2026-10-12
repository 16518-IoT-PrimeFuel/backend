package com.primefuel.fulltank.platform.notification;

import com.primefuel.fulltank.platform.iam.api.LegacyCompanyDirectory;
import com.primefuel.fulltank.platform.iam.application.commandservices.MembershipCommandService;
import com.primefuel.fulltank.platform.iam.application.commandservices.OnboardingCommandService;
import com.primefuel.fulltank.platform.iam.domain.model.commands.GrantMembershipCommand;
import com.primefuel.fulltank.platform.iam.domain.model.commands.OnboardOrganizationCommand;
import com.primefuel.fulltank.platform.iam.domain.model.valueobjects.MembershipRole;
import com.primefuel.fulltank.platform.iam.domain.model.valueobjects.OrganizationType;
import com.primefuel.fulltank.platform.inventory.application.commandservices.FuelProductCommandService;
import com.primefuel.fulltank.platform.inventory.domain.model.commands.CreateFuelProductCommand;
import com.primefuel.fulltank.platform.inventory.domain.model.valueobjects.FuelType;
import com.primefuel.fulltank.platform.notification.application.internal.listeners.NotificationFanoutListener;
import com.primefuel.fulltank.platform.notification.domain.model.aggregates.Notification;
import com.primefuel.fulltank.platform.notification.domain.model.valueobjects.NotificationType;
import com.primefuel.fulltank.platform.notification.domain.repositories.NotificationRepository;
import com.primefuel.fulltank.platform.replenishment.application.commandservices.ReplenishmentCommandService;
import com.primefuel.fulltank.platform.replenishment.domain.model.commands.CreateReplenishmentRequestCommand;
import com.primefuel.fulltank.platform.replenishment.domain.model.valueobjects.ReplenishmentSource;
import com.primefuel.fulltank.platform.shared.events.EventEnvelope;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * US-30: a new request notifies only the distributor's members (NEW_REQUEST), and a dispatched delivery notifies
 * only the buyer's members (ORDER_DISPATCHED). Each one is scoped by organization, so other tenants see nothing.
 */
@SpringBootTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:notification_dispatch_isolation;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "authorization.jwt.secret=0123456789abcdef0123456789abcdef"
})
class DispatchAndNewRequestNotificationTest {

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Autowired private OnboardingCommandService onboardingCommandService;
    @Autowired private MembershipCommandService membershipCommandService;
    @Autowired private ReplenishmentCommandService replenishmentCommandService;
    @Autowired private FuelProductCommandService fuelProductCommandService;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private NotificationFanoutListener fanoutListener;

    @MockitoBean private LegacyCompanyDirectory companies;

    private long onboard(OrganizationType type, long ownerUserId) {
        var organization = onboardingCommandService.handle(new OnboardOrganizationCommand(
                "Dispatch Org " + SEQUENCE.incrementAndGet(), "DSP" + SEQUENCE.incrementAndGet(), type, ownerUserId));
        assertThat(organization.isSuccess()).isTrue();
        return organization.getOrElse(null).getId();
    }

    private void grant(long organizationId, long userId) {
        assertThat(membershipCommandService.handle(new GrantMembershipCommand(
                organizationId, userId, MembershipRole.MEMBER)).isSuccess()).isTrue();
    }

    private long product(long providerId) {
        var product = fuelProductCommandService.handle(new CreateFuelProductCommand(
                "Dispatch Diesel " + SEQUENCE.incrementAndGet(), FuelType.DIESEL, 10.0, "GALLONS", 500.0, 1000.0,
                providerId, true));
        assertThat(product.isSuccess()).isTrue();
        return product.getOrElse(null).getId();
    }

    private long request(long buyerOrganizationId, long providerId, long productId) {
        var created = replenishmentCommandService.handle(new CreateReplenishmentRequestCommand(
                buyerOrganizationId, null, 100000L + SEQUENCE.incrementAndGet(), providerId, productId, 10.0,
                "GALLONS", ReplenishmentSource.MANUAL,
                "dispatch-" + SEQUENCE.incrementAndGet(), "Av. Prueba", null));
        assertThat(created.isSuccess()).isTrue();
        return created.getOrElse(null).getId();
    }

    private List<Notification> notificationsFor(long userId, NotificationType type) {
        return notificationRepository.findByUserId(userId).stream()
                .filter(notification -> notification.getType() == type)
                .toList();
    }

    @Test
    void aNewRequestNotifiesTheDistributorMembersAndNoOtherTenant() {
        long distributor = onboard(OrganizationType.DISTRIBUTOR, 3001);
        grant(distributor, 3002);
        long buyer = onboard(OrganizationType.CUSTOMER, 3003);
        long providerId = 77L;
        when(companies.organizationIdForProvider(providerId)).thenReturn(Optional.of(distributor));

        long requestId = request(buyer, providerId, product(providerId));

        assertThat(notificationsFor(3001, NotificationType.NEW_REQUEST)).hasSize(1);
        assertThat(notificationsFor(3002, NotificationType.NEW_REQUEST)).hasSize(1);
        assertThat(notificationsFor(3003, NotificationType.NEW_REQUEST)).isEmpty();
        var notification = notificationsFor(3001, NotificationType.NEW_REQUEST).get(0);
        assertThat(notification.getOrganizationId()).isEqualTo(distributor);
        assertThat(notification.getReferenceId()).isEqualTo(requestId);
    }

    @Test
    void aRequestWithoutAResolvableDistributorNotifiesNobody() {
        long buyer = onboard(OrganizationType.CUSTOMER, 3010);
        long providerId = 78L;
        when(companies.organizationIdForProvider(providerId)).thenReturn(Optional.empty());

        long requestId = request(buyer, providerId, product(providerId));

        assertThat(notificationRepository.findByUserId(3010L))
                .noneMatch(notification -> notification.getType() == NotificationType.NEW_REQUEST
                        && Long.valueOf(requestId).equals(notification.getReferenceId()));
    }

    @Test
    void anAssignedDeliveryNotifiesTheBuyerOnceAndNotTheDistributor() {
        long distributor = onboard(OrganizationType.DISTRIBUTOR, 3020);
        long buyer = onboard(OrganizationType.CUSTOMER, 3021);
        grant(buyer, 3022);
        var envelope = new EventEnvelope(UUID.randomUUID(), "order.dispatched.v1", "Delivery", "501",
                buyer, 1L, Instant.now(), "{\"deliveryId\":501,\"orderId\":9,\"providerId\":77}");

        fanoutListener.on(envelope);
        fanoutListener.on(envelope); // replay of the same event: same recipients, no second row

        assertThat(notificationsFor(3021, NotificationType.ORDER_DISPATCHED)).hasSize(1);
        assertThat(notificationsFor(3022, NotificationType.ORDER_DISPATCHED)).hasSize(1);
        assertThat(notificationsFor(3020, NotificationType.ORDER_DISPATCHED)).isEmpty();
        assertThat(distributor).isNotEqualTo(buyer);
    }
}
