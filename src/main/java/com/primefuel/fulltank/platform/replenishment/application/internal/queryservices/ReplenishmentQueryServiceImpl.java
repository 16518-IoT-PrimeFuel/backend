package com.primefuel.fulltank.platform.replenishment.application.internal.queryservices;

import com.primefuel.fulltank.platform.replenishment.application.queryservices.ReplenishmentQueryService;
import com.primefuel.fulltank.platform.replenishment.domain.model.aggregates.ReplenishmentRequest;
import com.primefuel.fulltank.platform.replenishment.domain.model.queries.GetReplenishmentRequestByIdQuery;
import com.primefuel.fulltank.platform.replenishment.domain.model.queries.GetReplenishmentRequestsByOrganizationQuery;
import com.primefuel.fulltank.platform.replenishment.domain.repositories.ReplenishmentRequestRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class ReplenishmentQueryServiceImpl implements ReplenishmentQueryService {

    private final ReplenishmentRequestRepository repository;

    public ReplenishmentQueryServiceImpl(ReplenishmentRequestRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<ReplenishmentRequest> handle(GetReplenishmentRequestByIdQuery query) {
        return repository.findById(query.requestId());
    }

    @Override
    public List<ReplenishmentRequest> handle(GetReplenishmentRequestsByOrganizationQuery query) {
        return repository.findByOrganizationId(query.organizationId());
    }
}
