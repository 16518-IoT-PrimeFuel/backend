package com.primefuel.fulltank.platform.iam.application.queryservices;

import com.primefuel.fulltank.platform.iam.domain.model.aggregates.Membership;
import com.primefuel.fulltank.platform.iam.domain.model.queries.GetMembershipsByOrganizationIdQuery;
import com.primefuel.fulltank.platform.iam.domain.model.queries.GetMembershipsByUserIdQuery;

import java.util.List;

public interface MembershipQueryService {
    List<Membership> handle(GetMembershipsByUserIdQuery query);

    /** Membresías activas de la organización. */
    List<Membership> handle(GetMembershipsByOrganizationIdQuery query);
}
