package com.primefuel.fulltank.platform.iam.application.commandservices;

import com.primefuel.fulltank.platform.iam.domain.model.aggregates.Organization;
import com.primefuel.fulltank.platform.iam.domain.model.commands.OnboardOrganizationCommand;
import com.primefuel.fulltank.platform.shared.application.result.ApplicationError;
import com.primefuel.fulltank.platform.shared.application.result.Result;

public interface OnboardingCommandService {
    Result<Organization, ApplicationError> handle(OnboardOrganizationCommand command);
}
