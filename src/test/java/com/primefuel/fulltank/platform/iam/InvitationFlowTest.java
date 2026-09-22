package com.primefuel.fulltank.platform.iam;

import com.primefuel.fulltank.platform.iam.application.commandservices.InvitationCommandService;
import com.primefuel.fulltank.platform.iam.application.commandservices.OrganizationCommandService;
import com.primefuel.fulltank.platform.iam.domain.model.aggregates.OrganizationInvitation;
import com.primefuel.fulltank.platform.iam.domain.model.commands.AcceptInvitationCommand;
import com.primefuel.fulltank.platform.iam.domain.model.commands.CreateOrganizationCommand;
import com.primefuel.fulltank.platform.iam.domain.model.commands.InviteMemberCommand;
import com.primefuel.fulltank.platform.iam.domain.model.commands.RevokeInvitationCommand;
import com.primefuel.fulltank.platform.iam.domain.model.valueobjects.InvitationStatus;
import com.primefuel.fulltank.platform.iam.domain.model.valueobjects.MembershipRole;
import com.primefuel.fulltank.platform.iam.domain.model.valueobjects.OrganizationType;
import com.primefuel.fulltank.platform.iam.domain.repositories.MembershipRepository;
import com.primefuel.fulltank.platform.iam.domain.repositories.OrganizationInvitationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:invitation_flow;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "authorization.jwt.secret=0123456789abcdef0123456789abcdef"
})
class InvitationFlowTest {

    @Autowired
    private OrganizationCommandService organizationCommandService;

    @Autowired
    private InvitationCommandService invitationCommandService;

    @Autowired
    private OrganizationInvitationRepository invitationRepository;

    @Autowired
    private MembershipRepository membershipRepository;

    @Test
    void invitationLifecycleEnforcesExpiryDuplicateAndRevocation() {
        var organization = organizationCommandService.handle(
                new CreateOrganizationCommand("Invite Co", "20111111111", OrganizationType.DISTRIBUTOR));
        assertThat(organization.isSuccess()).isTrue();
        var organizationId = organization.getOrElse(null).getId();

        var invited = invitationCommandService.handle(
                new InviteMemberCommand(organizationId, "member@example.com", MembershipRole.MEMBER, 1L));
        assertThat(invited.isSuccess()).isTrue();
        var token = invited.getOrElse(null).getToken();
        assertThat(invited.getOrElse(null).getStatus()).isEqualTo(InvitationStatus.PENDING);

        var duplicate = invitationCommandService.handle(
                new InviteMemberCommand(organizationId, "member@example.com", MembershipRole.MEMBER, 1L));
        assertThat(duplicate.isFailure()).isTrue();

        var accepted = invitationCommandService.handle(new AcceptInvitationCommand(token, 7L));
        assertThat(accepted.isSuccess()).isTrue();
        assertThat(accepted.getOrElse(null).getStatus()).isEqualTo(InvitationStatus.ACCEPTED);
        var membership = membershipRepository.findByOrganizationIdAndUserId(organizationId, 7L);
        assertThat(membership).isPresent();
        assertThat(membership.get().isActive()).isTrue();

        var reuseToken = invitationCommandService.handle(new AcceptInvitationCommand(token, 8L));
        assertThat(reuseToken.isFailure()).isTrue();

        var expired = new OrganizationInvitation(
                new InviteMemberCommand(organizationId, "expired@example.com", MembershipRole.MEMBER, 1L),
                "expired-token", Instant.now().minus(Duration.ofDays(1)));
        invitationRepository.save(expired);
        var attemptExpired = invitationCommandService.handle(new AcceptInvitationCommand("expired-token", 9L));
        assertThat(attemptExpired.isFailure()).isTrue();

        var revocable = invitationCommandService.handle(
                new InviteMemberCommand(organizationId, "revoked@example.com", MembershipRole.ADMIN, 1L));
        var revocableId = revocable.getOrElse(null).getId();
        var revocableToken = revocable.getOrElse(null).getToken();
        var revoked = invitationCommandService.handle(new RevokeInvitationCommand(revocableId));
        assertThat(revoked.isSuccess()).isTrue();
        assertThat(revoked.getOrElse(null).getStatus()).isEqualTo(InvitationStatus.REVOKED);

        var attemptRevoked = invitationCommandService.handle(new AcceptInvitationCommand(revocableToken, 10L));
        assertThat(attemptRevoked.isFailure()).isTrue();
        assertThat(membershipRepository.findByOrganizationIdAndUserId(organizationId, 10L)).isEmpty();
    }
}
