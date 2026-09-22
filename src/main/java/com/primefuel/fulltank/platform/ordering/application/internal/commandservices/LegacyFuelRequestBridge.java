package com.primefuel.fulltank.platform.ordering.application.internal.commandservices;

import com.primefuel.fulltank.platform.equipment.api.CustomerDirectory;
import com.primefuel.fulltank.platform.equipment.api.TankAssets;
import com.primefuel.fulltank.platform.ordering.domain.model.aggregates.FuelOrder;
import com.primefuel.fulltank.platform.ordering.infrastructure.persistence.jpa.entities.FuelRequestPersistenceEntity;
import com.primefuel.fulltank.platform.ordering.interfaces.rest.resources.CreateFuelRequestResource;
import com.primefuel.fulltank.platform.replenishment.api.ReplenishmentLookup;
import com.primefuel.fulltank.platform.replenishment.application.commandservices.ReplenishmentCommandService;
import com.primefuel.fulltank.platform.replenishment.domain.model.commands.AcceptReplenishmentRequestCommand;
import com.primefuel.fulltank.platform.replenishment.domain.model.commands.AttachReplenishmentOrderCommand;
import com.primefuel.fulltank.platform.replenishment.domain.model.commands.ConsumeReplenishmentAcceptanceCommand;
import com.primefuel.fulltank.platform.replenishment.domain.model.commands.CreateReplenishmentRequestCommand;
import com.primefuel.fulltank.platform.replenishment.domain.model.commands.RejectReplenishmentRequestCommand;
import com.primefuel.fulltank.platform.replenishment.domain.model.valueobjects.ReplenishmentSource;
import com.primefuel.fulltank.platform.shared.application.result.Result;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * T10-B bridge: routes the legacy v1 fuel-request flow through the {@code replenishment} module while
 * preserving legacy identity. Creating a v1 request also creates a ReplenishmentRequest correlated by
 * an episode key derived from the legacy id; accepting consumes that acceptance exactly once and then
 * lets the legacy service create the order (legacy request id preserved on the order); rejecting
 * propagates the decision. Legacy rows without a linked request keep working unchanged.
 *
 * <p>This lives in its own component (rather than inside {@code FuelRequestService}) so the frozen
 * ArchUnit baseline of that legacy class is not disturbed.
 */
@Service
public class LegacyFuelRequestBridge {
    private static final String EPISODE_PREFIX = "fuel-request:";

    private final FuelRequestService fuelRequestService;
    private final ReplenishmentCommandService replenishmentCommandService;
    private final ReplenishmentLookup replenishmentLookup;
    private final CustomerDirectory customerDirectory;
    private final TankAssets tankAssets;

    public LegacyFuelRequestBridge(FuelRequestService fuelRequestService,
                                   ReplenishmentCommandService replenishmentCommandService,
                                   ReplenishmentLookup replenishmentLookup,
                                   CustomerDirectory customerDirectory,
                                   TankAssets tankAssets) {
        this.fuelRequestService = fuelRequestService;
        this.replenishmentCommandService = replenishmentCommandService;
        this.replenishmentLookup = replenishmentLookup;
        this.customerDirectory = customerDirectory;
        this.tankAssets = tankAssets;
    }

    @Transactional
    public FuelRequestPersistenceEntity create(CreateFuelRequestResource resource) {
        var saved = fuelRequestService.create(resource);
        var customerId = customerDirectory.customerIdForLegacyCompany(saved.getBuyerCompanyId()).orElse(null);
        var organizationId = customerId == null
                ? saved.getBuyerCompanyId()
                : customerDirectory.organizationIdForCustomer(customerId).orElse(saved.getBuyerCompanyId());
        var tankId = tankAssets.tankIdForLegacyEquipment(saved.getEquipmentId()).orElse(null);
        var source = resource.source() == null || resource.source().equalsIgnoreCase("MANUAL")
                ? ReplenishmentSource.MANUAL : ReplenishmentSource.AUTOMATIC;
        replenishmentCommandService.handle(new CreateReplenishmentRequestCommand(
                organizationId, customerId, tankId, saved.getProviderId(), saved.getFuelProductId(),
                saved.getQuantity(), saved.getUnit(), source, episodeKey(saved.getId())));
        return saved;
    }

    @Transactional
    public FuelOrder accept(Long requestId) {
        var linked = replenishmentLookup.findByEpisodeKey(episodeKey(requestId));
        if (linked.isPresent()) {
            var id = linked.get().id();
            var accepted = replenishmentCommandService.handle(new AcceptReplenishmentRequestCommand(id));
            if (accepted instanceof Result.Failure<?, ?> failure) {
                throw new IllegalStateException("The review could not be accepted: " + failure.error());
            }
            var consumed = replenishmentCommandService.handle(new ConsumeReplenishmentAcceptanceCommand(id));
            if (consumed instanceof Result.Failure<?, ?> || !consumed.getOrElse(false)) {
                throw new IllegalStateException("This request was already accepted");
            }
        }
        var order = fuelRequestService.accept(requestId);
        linked.ifPresent(view -> replenishmentCommandService.handle(
                new AttachReplenishmentOrderCommand(view.id(), order.getId())));
        return order;
    }

    @Transactional
    public FuelRequestPersistenceEntity reject(Long requestId, String reason) {
        var rejected = fuelRequestService.reject(requestId, reason);
        replenishmentLookup.findByEpisodeKey(episodeKey(requestId)).ifPresent(linked ->
                replenishmentCommandService.handle(
                        new RejectReplenishmentRequestCommand(linked.id(), reason.trim())));
        return rejected;
    }

    private static String episodeKey(Long requestId) {
        return EPISODE_PREFIX + requestId;
    }
}
