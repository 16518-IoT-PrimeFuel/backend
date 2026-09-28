package com.primefuel.fulltank.platform.replenishment.application.internal.commandservices;

import com.primefuel.fulltank.platform.equipment.api.CustomerDirectory;
import com.primefuel.fulltank.platform.equipment.api.TankAssets;
import com.primefuel.fulltank.platform.replenishment.application.commandservices.ReplenishmentCommandService;
import com.primefuel.fulltank.platform.replenishment.domain.model.aggregates.ReplenishmentRequest;
import com.primefuel.fulltank.platform.replenishment.domain.model.commands.AcceptReplenishmentRequestCommand;
import com.primefuel.fulltank.platform.replenishment.domain.model.commands.AttachReplenishmentOrderCommand;
import com.primefuel.fulltank.platform.replenishment.domain.model.commands.CancelReplenishmentRequestCommand;
import com.primefuel.fulltank.platform.replenishment.domain.model.commands.ConsumeReplenishmentAcceptanceCommand;
import com.primefuel.fulltank.platform.replenishment.domain.model.commands.CreateReplenishmentRequestCommand;
import com.primefuel.fulltank.platform.replenishment.domain.model.commands.RejectReplenishmentRequestCommand;
import com.primefuel.fulltank.platform.replenishment.domain.repositories.ReplenishmentRequestRepository;
import com.primefuel.fulltank.platform.shared.application.result.ApplicationError;
import com.primefuel.fulltank.platform.shared.application.result.Result;
import com.primefuel.fulltank.platform.shared.events.EventPublicationRegistry;
import com.primefuel.fulltank.platform.supply.api.SupplyCatalog;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

@Service
public class ReplenishmentCommandServiceImpl implements ReplenishmentCommandService {

    private static final String AGGREGATE_TYPE = "ReplenishmentRequest";
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("America/Lima");

    private final ReplenishmentRequestRepository repository;
    private final SupplyCatalog supplyCatalog;
    private final EventPublicationRegistry publicationRegistry;
    private final CustomerDirectory customers;
    private final TankAssets tanks;
    private final Clock clock;

    public ReplenishmentCommandServiceImpl(ReplenishmentRequestRepository repository,
                                           SupplyCatalog supplyCatalog,
                                           EventPublicationRegistry publicationRegistry,
                                           CustomerDirectory customers,
                                           TankAssets tanks,
                                           Clock clock) {
        this.repository = repository;
        this.supplyCatalog = supplyCatalog;
        this.publicationRegistry = publicationRegistry;
        this.customers = customers;
        this.tanks = tanks;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Result<ReplenishmentRequest, ApplicationError> handle(CreateReplenishmentRequestCommand command) {
        if (command.organizationId() == null) {
            return Result.failure(ApplicationError.validationError("organization", "An organization is required"));
        }
        if (command.customerAccountId() != null
                && !customers.ownsCustomer(command.organizationId(), command.customerAccountId())) {
            return Result.failure(ApplicationError.notFound("CustomerAccount", "not found"));
        }
        if (command.customerAccountId() != null && command.tankId() != null && tanks.findById(command.tankId())
                .filter(tank -> command.organizationId().equals(tank.organizationId())
                        && command.customerAccountId().equals(tank.customerAccountId()))
                .isEmpty()) {
            return Result.failure(ApplicationError.notFound("Tank", "not found"));
        }
        var today = LocalDate.now(clock.withZone(BUSINESS_ZONE));
        var deliveryDate = command.deliveryDate() == null ? today : command.deliveryDate();
        if (deliveryDate.isBefore(today)) {
            return Result.failure(ApplicationError.validationError(
                    "deliveryDate", "Delivery date cannot be in the past"));
        }
        var deliveryAddress = command.deliveryAddress();
        if (deliveryAddress == null || deliveryAddress.isBlank()) {
            deliveryAddress = command.tankId() == null ? null
                    : tanks.deliveryAddressForTank(command.tankId()).orElse(null);
        }
        if (deliveryAddress == null || deliveryAddress.isBlank()) {
            return Result.failure(ApplicationError.validationError(
                    "deliveryAddress", "A delivery address is required"));
        }
        if (command.episodeKey() != null) {
            var existing = repository.findByEpisodeKey(command.episodeKey());
            if (existing.isPresent()) {
                // Idempotent by episode key: a single request per episode.
                return Result.success(existing.get());
            }
        }
        var snapshot = supplyCatalog.findForTenant(command.providerId(), command.fuelProductId());
        if (snapshot.isEmpty()) {
            return Result.failure(ApplicationError.notFound("FuelProduct", String.valueOf(command.fuelProductId())));
        }
        if (!snapshot.get().active()) {
            return Result.failure(ApplicationError.validationError("fuelProduct", "Fuel product is inactive"));
        }
        try {
            var request = new ReplenishmentRequest(new CreateReplenishmentRequestCommand(
                    command.organizationId(), command.customerAccountId(), command.tankId(), command.providerId(),
                    command.fuelProductId(), command.quantity(), command.unit(), command.source(), command.episodeKey(),
                    deliveryAddress.trim(), deliveryDate), snapshot.get().pricePerUnit());
            return Result.success(repository.save(request));
        } catch (IllegalArgumentException exception) {
            return Result.failure(ApplicationError.validationError("replenishment", exception.getMessage()));
        }
    }

    @Override
    @Transactional
    public Result<ReplenishmentRequest, ApplicationError> handle(AcceptReplenishmentRequestCommand command) {
        return transition(command.requestId(), request -> request.accept(null))
                .map(request -> {
                    publishDecision(request, "replenishment.accepted.v1");
                    return request;
                });
    }

    @Override
    @Transactional
    public Result<ReplenishmentRequest, ApplicationError> handle(RejectReplenishmentRequestCommand command) {
        return transition(command.requestId(), request -> request.reject(command.reason()))
                .map(request -> {
                    publishDecision(request, "replenishment.rejected.v1");
                    return request;
                });
    }

    /**
     * S20/T20-A: the decision is published (durable outbox + in-process envelope) so the notification
     * fanout can reach the members of the requesting organization. The scope is the request's
     * organizationId — the client to be informed — not a trusted client-supplied value.
     */
    private void publishDecision(ReplenishmentRequest request, String eventType) {
        publicationRegistry.publish(eventType, AGGREGATE_TYPE, String.valueOf(request.getId()),
                request.getOrganizationId(), (long) request.getVersion(),
                "{\"requestId\":" + request.getId()
                        + ",\"organizationId\":" + request.getOrganizationId()
                        + ",\"providerId\":" + request.getProviderId()
                        + ",\"status\":\"" + request.getStatus().name() + "\"}");
    }

    @Override
    @Transactional
    public Result<ReplenishmentRequest, ApplicationError> handle(CancelReplenishmentRequestCommand command) {
        return transition(command.requestId(), request -> request.cancel());
    }

    @Override
    @Transactional
    public Result<Boolean, ApplicationError> handle(ConsumeReplenishmentAcceptanceCommand command) {
        var existing = repository.findById(command.requestId());
        if (existing.isEmpty()) {
            return Result.failure(ApplicationError.notFound("ReplenishmentRequest", String.valueOf(command.requestId())));
        }
        try {
            var consumed = existing.get().consumeAcceptance();
            repository.saveAndFlush(existing.get());
            return Result.success(consumed);
        } catch (IllegalStateException exception) {
            return Result.failure(ApplicationError.conflict("ReplenishmentRequest", exception.getMessage()));
        } catch (OptimisticLockingFailureException exception) {
            return Result.failure(ApplicationError.conflict(
                    "ReplenishmentRequest", "The request was decided concurrently"));
        }
    }

    @Override
    @Transactional
    public Result<ReplenishmentRequest, ApplicationError> handle(AttachReplenishmentOrderCommand command) {
        var existing = repository.findById(command.requestId());
        if (existing.isEmpty()) {
            return Result.failure(ApplicationError.notFound("ReplenishmentRequest", String.valueOf(command.requestId())));
        }
        var request = existing.get();
        try {
            request.attachOrder(command.orderId());
        } catch (IllegalStateException exception) {
            return Result.failure(ApplicationError.conflict("ReplenishmentRequest", exception.getMessage()));
        }
        return Result.success(repository.saveAndFlush(request));
    }

    private Result<ReplenishmentRequest, ApplicationError> transition(
            Long requestId, java.util.function.Consumer<ReplenishmentRequest> action) {
        var existing = repository.findById(requestId);
        if (existing.isEmpty()) {
            return Result.failure(ApplicationError.notFound("ReplenishmentRequest", String.valueOf(requestId)));
        }
        var request = existing.get();
        try {
            action.accept(request);
        } catch (IllegalArgumentException exception) {
            return Result.failure(ApplicationError.validationError("replenishment", exception.getMessage()));
        } catch (IllegalStateException exception) {
            return Result.failure(ApplicationError.conflict("ReplenishmentRequest", exception.getMessage()));
        }
        try {
            return Result.success(repository.saveAndFlush(request));
        } catch (OptimisticLockingFailureException exception) {
            // accept/reject race: exactly one decision wins.
            return Result.failure(ApplicationError.conflict(
                    "ReplenishmentRequest", "The request was decided concurrently"));
        }
    }
}
