package com.primefuel.fulltank.platform.iam.application.queryservices;

import com.primefuel.fulltank.platform.iam.domain.model.aggregates.Organization;
import com.primefuel.fulltank.platform.iam.domain.model.queries.GetOrganizationByIdQuery;

import java.util.Optional;

public interface OrganizationQueryService {
    Optional<Organization> handle(GetOrganizationByIdQuery query);
}
