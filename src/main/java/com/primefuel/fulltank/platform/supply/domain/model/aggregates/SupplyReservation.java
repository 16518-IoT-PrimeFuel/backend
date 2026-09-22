package com.primefuel.fulltank.platform.supply.domain.model.aggregates;

import com.primefuel.fulltank.platform.supply.domain.model.commands.ReserveSupplyCommand;
import com.primefuel.fulltank.platform.supply.domain.model.valueobjects.ReservationStatus;
import com.primefuel.fulltank.platform.shared.domain.model.valueobjects.Unit;
import com.primefuel.fulltank.platform.shared.domain.model.aggregates.AbstractDomainAggregateRoot;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A hold on a product's stock for a future delivery. It snapshots the unit price and unit at reserve
 * time so historical reconciliation never re-reads a mutated catalog (S11: snapshot histórico).
 */
@Getter
@Setter
@NoArgsConstructor
public class SupplyReservation extends AbstractDomainAggregateRoot<SupplyReservation> {

    private Long id;
    private Long providerId;
    private Long fuelProductId;
    private String reference;
    private double quantity;
    private String unit;
    private double unitPrice;
    private ReservationStatus status;

    public SupplyReservation(ReserveSupplyCommand command, double unitPrice) {
        this.providerId = command.providerId();
        this.fuelProductId = command.fuelProductId();
        this.reference = command.reference();
        this.quantity = command.quantity();
        this.unit = Unit.fromCode(command.unit()).name();
        this.unitPrice = unitPrice;
        this.status = ReservationStatus.ACTIVE;
    }

    public boolean isActive() {
        return status == ReservationStatus.ACTIVE;
    }

    public void release() {
        this.status = ReservationStatus.RELEASED;
    }

    public void reconcile() {
        this.status = ReservationStatus.RECONCILED;
    }
}
