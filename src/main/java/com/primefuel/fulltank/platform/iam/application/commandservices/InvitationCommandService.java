package com.primefuel.fulltank.platform.iam.application.commandservices;

import com.primefuel.fulltank.platform.iam.domain.model.aggregates.OrganizationInvitation;
import com.primefuel.fulltank.platform.iam.domain.model.commands.AcceptInvitationCommand;
import com.primefuel.fulltank.platform.iam.domain.model.commands.InviteMemberCommand;
import com.primefuel.fulltank.platform.iam.domain.model.commands.RevokeInvitationCommand;
import com.primefuel.fulltank.platform.shared.application.result.ApplicationError;
import com.primefuel.fulltank.platform.shared.application.result.Result;

public interface InvitationCommandService {
    Result<OrganizationInvitation, ApplicationError> handle(InviteMemberCommand command);
    Result<OrganizationInvitation, ApplicationError> handle(AcceptInvitationCommand command);
    Result<OrganizationInvitation, ApplicationError> handle(RevokeInvitationCommand command);
}
