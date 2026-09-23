package com.primefuel.fulltank.platform.replenishment.infrastructure.services;

import com.primefuel.fulltank.platform.replenishment.api.ReplenishmentLookup;
import com.primefuel.fulltank.platform.replenishment.domain.repositories.ReplenishmentRequestRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component("replenishmentLookup")
public class ReplenishmentLookupImpl implements ReplenishmentLookup {

    private final ReplenishmentRequestRepository repository;

    public ReplenishmentLookupImpl(ReplenishmentRequestRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<ReplenishmentView> findById(Long requestId) {
        return repository.findById(requestId).map(this::toView);
    }

    @Override
    public Optional<ReplenishmentView> findByEpisodeKey(String episodeKey) {
        if (episodeKey == null) {
            return Optional.empty();
        }
        return repository.findByEpisodeKey(episodeKey).map(this::toView);
    }

    @Override
    public Optional<ReplenishmentView> findByOrderId(Long orderId) {
        if (orderId == null) {
            return Optional.empty();
        }
        return repository.findByOrderId(orderId).map(this::toView);
    }

    private ReplenishmentView toView(com.primefuel.fulltank.platform.replenishment.domain.model.aggregates.ReplenishmentRequest request) {
        return new ReplenishmentView(
                request.getId(),
                request.getOrganizationId(),
                request.getProviderId(),
                request.getFuelProductId(),
                request.getTankId(),
                request.getQuantity(),
                request.getUnit(),
                request.getUnitPrice(),
                request.getStatus() == null ? null : request.getStatus().name(),
                request.getOrderId());
    }
}
