package com.primefuel.fulltank.platform.iam.application.queryservices;

import com.primefuel.fulltank.platform.iam.domain.model.aggregates.OrganizationInvitation;
import com.primefuel.fulltank.platform.iam.domain.model.queries.GetInvitationByIdQuery;
import com.primefuel.fulltank.platform.iam.domain.model.queries.GetInvitationByTokenQuery;

import java.util.Optional;

public interface InvitationQueryService {
    Optional<OrganizationInvitation> handle(GetInvitationByTokenQuery query);
    Optional<OrganizationInvitation> handle(GetInvitationByIdQuery query);
}
