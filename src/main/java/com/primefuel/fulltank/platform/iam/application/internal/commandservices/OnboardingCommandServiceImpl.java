package com.primefuel.fulltank.platform.iam.application.internal.commandservices;

import com.primefuel.fulltank.platform.iam.application.commandservices.MembershipCommandService;
import com.primefuel.fulltank.platform.iam.application.commandservices.OnboardingCommandService;
import com.primefuel.fulltank.platform.iam.application.commandservices.OrganizationCommandService;
import com.primefuel.fulltank.platform.iam.domain.model.aggregates.Organization;
import com.primefuel.fulltank.platform.iam.domain.model.commands.CreateOrganizationCommand;
import com.primefuel.fulltank.platform.iam.domain.model.commands.GrantMembershipCommand;
import com.primefuel.fulltank.platform.iam.domain.model.commands.OnboardOrganizationCommand;
import com.primefuel.fulltank.platform.iam.domain.model.valueobjects.MembershipRole;
import com.primefuel.fulltank.platform.shared.application.result.ApplicationError;
import com.primefuel.fulltank.platform.shared.application.result.Result;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OnboardingCommandServiceImpl implements OnboardingCommandService {

    private final OrganizationCommandService organizationCommandService;
    private final MembershipCommandService membershipCommandService;

    public OnboardingCommandServiceImpl(OrganizationCommandService organizationCommandService,
                                        MembershipCommandService membershipCommandService) {
        this.organizationCommandService = organizationCommandService;
        this.membershipCommandService = membershipCommandService;
    }

    @Override
    @Transactional
    public Result<Organization, ApplicationError> handle(OnboardOrganizationCommand command) {
        if (command.ownerUserId() == null) {
            return Result.failure(ApplicationError.validationError("owner", "An owner user is required to onboard"));
        }
        return organizationCommandService.handle(
                        new CreateOrganizationCommand(command.name(), command.ruc(), command.type()))
                .flatMap(organization -> membershipCommandService.handle(new GrantMembershipCommand(
                                organization.getId(), command.ownerUserId(), MembershipRole.OWNER))
                        .map(membership -> organization));
    }
}
