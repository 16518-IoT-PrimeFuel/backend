package com.primefuel.fulltank.platform.replenishment.application.queryservices;

import com.primefuel.fulltank.platform.replenishment.domain.model.aggregates.ReplenishmentRequest;
import com.primefuel.fulltank.platform.replenishment.domain.model.queries.GetReplenishmentRequestByIdQuery;
import com.primefuel.fulltank.platform.replenishment.domain.model.queries.GetReplenishmentRequestsByOrganizationQuery;

import java.util.List;
import java.util.Optional;

public interface ReplenishmentQueryService {
    Optional<ReplenishmentRequest> handle(GetReplenishmentRequestByIdQuery query);
    List<ReplenishmentRequest> handle(GetReplenishmentRequestsByOrganizationQuery query);
}
