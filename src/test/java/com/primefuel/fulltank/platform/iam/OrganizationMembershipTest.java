package com.primefuel.fulltank.platform.iam;

import com.primefuel.fulltank.platform.iam.application.commandservices.MembershipCommandService;
import com.primefuel.fulltank.platform.iam.application.commandservices.OrganizationCommandService;
import com.primefuel.fulltank.platform.iam.domain.model.commands.CreateOrganizationCommand;
import com.primefuel.fulltank.platform.iam.domain.model.commands.GrantMembershipCommand;
import com.primefuel.fulltank.platform.iam.domain.model.commands.RevokeMembershipCommand;
import com.primefuel.fulltank.platform.iam.domain.model.valueobjects.MembershipRole;
import com.primefuel.fulltank.platform.iam.domain.model.valueobjects.OrganizationType;
import com.primefuel.fulltank.platform.iam.domain.repositories.MembershipRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:organization_membership;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "authorization.jwt.secret=0123456789abcdef0123456789abcdef"
})
class OrganizationMembershipTest {

    @Autowired
    private OrganizationCommandService organizationCommandService;

    @Autowired
    private MembershipCommandService membershipCommandService;

    @Autowired
    private MembershipRepository membershipRepository;

    @Test
    void createsAnOrganizationAndManagesItsMemberships() {
        var organization = organizationCommandService.handle(
                new CreateOrganizationCommand("Acme Fuel", "20123456789", OrganizationType.DISTRIBUTOR));
        assertThat(organization.isSuccess()).isTrue();
        var organizationId = organization.getOrElse(null).getId();
        assertThat(organizationId).isNotNull();

        var granted = membershipCommandService.handle(
                new GrantMembershipCommand(organizationId, 5L, MembershipRole.OWNER));
        assertThat(granted.isSuccess()).isTrue();
        var membershipId = granted.getOrElse(null).getId();

        var duplicate = membershipCommandService.handle(
                new GrantMembershipCommand(organizationId, 5L, MembershipRole.MEMBER));
        assertThat(duplicate.isFailure()).isTrue();

        var revoked = membershipCommandService.handle(new RevokeMembershipCommand(membershipId));
        assertThat(revoked.isSuccess()).isTrue();
        assertThat(membershipRepository.findById(membershipId).orElseThrow().isActive()).isFalse();
    }
}
