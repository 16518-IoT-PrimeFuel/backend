package com.primefuel.fulltank.platform.notification;

import com.primefuel.fulltank.platform.iam.application.commandservices.MembershipCommandService;
import com.primefuel.fulltank.platform.iam.application.commandservices.OnboardingCommandService;
import com.primefuel.fulltank.platform.iam.domain.model.commands.GrantMembershipCommand;
import com.primefuel.fulltank.platform.iam.domain.model.commands.OnboardOrganizationCommand;
import com.primefuel.fulltank.platform.iam.domain.model.commands.RevokeMembershipCommand;
import com.primefuel.fulltank.platform.iam.domain.model.valueobjects.MembershipRole;
import com.primefuel.fulltank.platform.iam.domain.model.valueobjects.OrganizationType;
import com.primefuel.fulltank.platform.inventory.application.commandservices.FuelProductCommandService;
import com.primefuel.fulltank.platform.inventory.domain.model.commands.CreateFuelProductCommand;
import com.primefuel.fulltank.platform.inventory.domain.model.valueobjects.FuelType;
import com.primefuel.fulltank.platform.notification.application.internal.listeners.NotificationFanoutListener;
import com.primefuel.fulltank.platform.notification.domain.model.aggregates.Notification;
import com.primefuel.fulltank.platform.notification.domain.model.valueobjects.NotificationChannel;
import com.primefuel.fulltank.platform.notification.domain.model.valueobjects.NotificationDeliveryStatus;
import com.primefuel.fulltank.platform.notification.domain.model.valueobjects.NotificationType;
import com.primefuel.fulltank.platform.notification.domain.repositories.NotificationRepository;
import com.primefuel.fulltank.platform.replenishment.application.commandservices.ReplenishmentCommandService;
import com.primefuel.fulltank.platform.replenishment.domain.model.commands.AcceptReplenishmentRequestCommand;
import com.primefuel.fulltank.platform.replenishment.domain.model.commands.CreateReplenishmentRequestCommand;
import com.primefuel.fulltank.platform.replenishment.domain.model.commands.RejectReplenishmentRequestCommand;
import com.primefuel.fulltank.platform.replenishment.domain.model.valueobjects.ReplenishmentSource;
import com.primefuel.fulltank.platform.shared.events.EventEnvelope;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T20-A: event fanout. A published event reaches exactly the active members of its scope, once per recipient
 * (replay-safe), and a revoked membership stops receiving new fanout.
 */
@SpringBootTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:notification_fanout;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "authorization.jwt.secret=0123456789abcdef0123456789abcdef"
})
class NotificationFanoutTest {

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

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

    @Autowired
    private NotificationFanoutListener fanoutListener;

    private long onboard(long ownerUserId) {
        var organization = onboardingCommandService.handle(new OnboardOrganizationCommand(
                "Fanout Org " + SEQUENCE.incrementAndGet(), "FAN" + SEQUENCE.incrementAndGet(),
                OrganizationType.CUSTOMER, ownerUserId));
        assertThat(organization.isSuccess()).isTrue();
        return organization.getOrElse(null).getId();
    }

    private long grant(long organizationId, long userId) {
        var membership = membershipCommandService.handle(new GrantMembershipCommand(
                organizationId, userId, MembershipRole.MEMBER));
        assertThat(membership.isSuccess()).isTrue();
        return membership.getOrElse(null).getId();
    }

    private long product(long providerId) {
        var product = fuelProductCommandService.handle(new CreateFuelProductCommand(
                "Fanout Diesel " + SEQUENCE.incrementAndGet(), FuelType.DIESEL, 10.0, "GALLONS", 500.0, 1000.0,
                providerId, true));
        assertThat(product.isSuccess()).isTrue();
        return product.getOrElse(null).getId();
    }

    private long request(long organizationId, long providerId, long productId) {
        var created = replenishmentCommandService.handle(new CreateReplenishmentRequestCommand(
                organizationId, null, null, providerId, productId, 10.0, "GALLONS", ReplenishmentSource.MANUAL,
                "fanout-" + SEQUENCE.incrementAndGet()));
        assertThat(created.isSuccess()).isTrue();
        return created.getOrElse(null).getId();
    }

    private List<Notification> notificationsFor(long userId, NotificationType type) {
        return notificationRepository.findByUserId(userId).stream()
                .filter(notification -> notification.getType() == type)
                .toList();
    }

    @Test
    void anAcceptedRequestFansOutToEveryActiveMemberOfItsOrganization() {
        long ownerUserId = 1001;
        long memberUserId = 1002;
        long organizationId = onboard(ownerUserId);
        grant(organizationId, memberUserId);
        long providerId = 1;
        long productId = product(providerId);
        long requestId = request(organizationId, providerId, productId);

        assertThat(replenishmentCommandService.handle(new AcceptReplenishmentRequestCommand(requestId)).isSuccess())
                .isTrue();

        var ownerNotifications = notificationsFor(ownerUserId, NotificationType.ORDER_ACCEPTED);
        var memberNotifications = notificationsFor(memberUserId, NotificationType.ORDER_ACCEPTED);
        assertThat(ownerNotifications).hasSize(1);
        assertThat(memberNotifications).hasSize(1);
        var notification = ownerNotifications.get(0);
        assertThat(notification.getOrganizationId()).isEqualTo(organizationId);
        assertThat(notification.getEventId()).isNotBlank();
        assertThat(notification.getChannel()).isEqualTo(NotificationChannel.IN_APP);
        assertThat(notification.getDeliveryStatus()).isEqualTo(NotificationDeliveryStatus.DELIVERED);
        assertThat(notification.getAttempts()).isEqualTo(1);
        assertThat(notification.getReferenceId()).isEqualTo(requestId);
    }

    @Test
    void aRejectedRequestFansOutAsRejected() {
        long ownerUserId = 1003;
        long organizationId = onboard(ownerUserId);
        long providerId = 2;
        long productId = product(providerId);
        long requestId = request(organizationId, providerId, productId);

        assertThat(replenishmentCommandService.handle(
                new RejectReplenishmentRequestCommand(requestId, "sin stock")).isSuccess()).isTrue();

        assertThat(notificationsFor(ownerUserId, NotificationType.ORDER_REJECTED)).hasSize(1);
    }

    @Test
    void replayingTheSameEventDoesNotDuplicateTheFanout() {
        long ownerUserId = 1004;
        long organizationId = onboard(ownerUserId);
        var envelope = new EventEnvelope(UUID.randomUUID(), "replenishment.accepted.v1",
                "ReplenishmentRequest", "42", organizationId, 1L, Instant.now(), "{}");

        fanoutListener.on(envelope);
        fanoutListener.on(envelope);

        assertThat(notificationsFor(ownerUserId, NotificationType.ORDER_ACCEPTED)).hasSize(1);
    }

    @Test
    void aRevokedMemberStopsReceivingNewFanout() {
        long ownerUserId = 1005;
        long revokedUserId = 1006;
        long organizationId = onboard(ownerUserId);
        long membershipId = grant(organizationId, revokedUserId);
        assertThat(membershipCommandService.handle(new RevokeMembershipCommand(membershipId)).isSuccess()).isTrue();
        long providerId = 3;
        long productId = product(providerId);
        long requestId = request(organizationId, providerId, productId);

        assertThat(replenishmentCommandService.handle(new AcceptReplenishmentRequestCommand(requestId)).isSuccess())
                .isTrue();

        // The owner still receives it; the revoked member does not.
        assertThat(notificationsFor(ownerUserId, NotificationType.ORDER_ACCEPTED)).hasSize(1);
        assertThat(notificationsFor(revokedUserId, NotificationType.ORDER_ACCEPTED)).isEmpty();
    }
}
