package com.primefuel.fulltank.platform.iam;

import com.primefuel.fulltank.platform.iam.application.commandservices.UserCommandService;
import com.primefuel.fulltank.platform.iam.domain.model.commands.CreateBuyerCompanyCommand;
import com.primefuel.fulltank.platform.iam.domain.model.commands.SignUpCommand;
import com.primefuel.fulltank.platform.iam.domain.model.entities.Role;
import com.primefuel.fulltank.platform.iam.domain.model.valueobjects.MembershipRole;
import com.primefuel.fulltank.platform.iam.domain.model.valueobjects.OrganizationType;
import com.primefuel.fulltank.platform.iam.domain.model.valueobjects.Roles;
import com.primefuel.fulltank.platform.iam.domain.repositories.MembershipRepository;
import com.primefuel.fulltank.platform.iam.domain.repositories.OrganizationRepository;
import com.primefuel.fulltank.platform.iam.domain.repositories.RoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:signup_onboarding;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "authorization.jwt.secret=0123456789abcdef0123456789abcdef"
})
class SignUpOnboardingTest {

    @Autowired
    private UserCommandService userCommandService;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private MembershipRepository membershipRepository;

    @Autowired
    private RoleRepository roleRepository;

    @BeforeEach
    void ensureRolesExist() {
        if (roleRepository.findByName(Roles.ROLE_BUYER).isEmpty()) {
            roleRepository.save(new Role(Roles.ROLE_BUYER));
        }
        if (roleRepository.findByName(Roles.ROLE_PROVIDER).isEmpty()) {
            roleRepository.save(new Role(Roles.ROLE_PROVIDER));
        }
    }

    @Test
    void signUpCreatesAnOrganizationAndOwnerMembership() {
        var command = new SignUpCommand(
                "newbuyer@example.com",
                "password123",
                List.of(new Role(Roles.ROLE_BUYER)),
                new CreateBuyerCompanyCommand("New Buyer", "20999999999", "retail",
                        "Av. Siempre Viva 123", "newbuyer@example.com", "999888777"),
                null);

        var result = userCommandService.handle(command);
        assertThat(result.isSuccess()).isTrue();
        var user = result.getOrElse(null);

        var organization = organizationRepository.findByRuc("20999999999");
        assertThat(organization).isPresent();
        assertThat(organization.get().getType()).isEqualTo(OrganizationType.CUSTOMER);

        var membership = membershipRepository.findByOrganizationIdAndUserId(
                organization.get().getId(), user.getId());
        assertThat(membership).isPresent();
        assertThat(membership.get().getRole()).isEqualTo(MembershipRole.OWNER);
        assertThat(membership.get().isActive()).isTrue();
    }
}
