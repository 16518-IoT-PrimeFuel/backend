package com.primefuel.fulltank.platform.iam.application.commandservices;

import com.primefuel.fulltank.platform.iam.domain.model.aggregates.Membership;
import com.primefuel.fulltank.platform.iam.domain.model.commands.GrantMembershipCommand;
import com.primefuel.fulltank.platform.iam.domain.model.commands.RevokeMembershipCommand;
import com.primefuel.fulltank.platform.shared.application.result.ApplicationError;
import com.primefuel.fulltank.platform.shared.application.result.Result;

public interface MembershipCommandService {
    Result<Membership, ApplicationError> handle(GrantMembershipCommand command);
    Result<Membership, ApplicationError> handle(RevokeMembershipCommand command);
}
