package com.primefuel.fulltank.platform.replenishment.domain.model.aggregates;

import com.primefuel.fulltank.platform.replenishment.domain.model.commands.CreateReplenishmentRequestCommand;
import com.primefuel.fulltank.platform.replenishment.domain.model.valueobjects.ReplenishmentSource;
import com.primefuel.fulltank.platform.replenishment.domain.model.valueobjects.ReplenishmentStatus;
import com.primefuel.fulltank.platform.shared.domain.model.aggregates.AbstractDomainAggregateRoot;
import com.primefuel.fulltank.platform.shared.domain.model.valueobjects.Unit;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The single reviewable intent behind a replenishment. It replaces the two legacy routes
 * (FuelRequest creation + FuelOrder on accept) with one aggregate that has a single terminal decision
 * and a once-only acceptance consumption.
 */
@Getter
@Setter
@NoArgsConstructor
public class ReplenishmentRequest extends AbstractDomainAggregateRoot<ReplenishmentRequest> {

    private Long id;
    private Long organizationId;
    private Long customerAccountId;
    private Long tankId;
    private Long providerId;
    private Long fuelProductId;
    private double quantity;
    private String unit;
    private double unitPrice;
    private ReplenishmentStatus status;
    private ReplenishmentSource source;
    private String episodeKey;
    private String rejectionReason;
    private Long orderId;
    private boolean acceptanceConsumed;
    private int version;

    public ReplenishmentRequest(CreateReplenishmentRequestCommand command, double unitPrice) {
        if (command.quantity() == null || command.quantity() <= 0) {
            throw new IllegalArgumentException("Requested quantity must be positive");
        }
        this.organizationId = command.organizationId();
        this.customerAccountId = command.customerAccountId();
        this.tankId = command.tankId();
        this.providerId = command.providerId();
        this.fuelProductId = command.fuelProductId();
        this.quantity = command.quantity();
        this.unit = Unit.fromCode(command.unit()).name();
        this.unitPrice = unitPrice;
        this.source = command.source() == null ? ReplenishmentSource.MANUAL : command.source();
        this.episodeKey = command.episodeKey();
        this.status = ReplenishmentStatus.PENDING;
        this.acceptanceConsumed = false;
        this.version = 0;
    }

    public boolean isPending() {
        return status == ReplenishmentStatus.PENDING;
    }

    public void accept(Long orderId) {
        requirePending("accept");
        this.status = ReplenishmentStatus.ACCEPTED;
        this.orderId = orderId;
    }

    public void reject(String reason) {
        requirePending("reject");
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("A rejection reason is required");
        }
        this.status = ReplenishmentStatus.REJECTED;
        this.rejectionReason = reason;
    }

    public void cancel() {
        requirePending("cancel");
        this.status = ReplenishmentStatus.CANCELLED;
    }

    /** Records the downstream order created from an accepted request (legacy correlation). */
    public void attachOrder(Long orderId) {
        if (status != ReplenishmentStatus.ACCEPTED) {
            throw new IllegalStateException("Only an accepted request can be correlated with an order");
        }
        this.orderId = orderId;
    }

    /** Once-only: the acceptance can be turned into a downstream effect exactly once. */
    public boolean consumeAcceptance() {
        if (status != ReplenishmentStatus.ACCEPTED) {
            throw new IllegalStateException("Only an accepted request can be consumed");
        }
        if (acceptanceConsumed) {
            return false;
        }
        this.acceptanceConsumed = true;
        return true;
    }

    private void requirePending(String action) {
        if (!isPending()) {
            throw new IllegalStateException("Only a pending request can be " + action + "ed");
        }
    }
}
