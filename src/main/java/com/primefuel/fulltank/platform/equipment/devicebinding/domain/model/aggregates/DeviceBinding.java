package com.primefuel.fulltank.platform.equipment.devicebinding.domain.model.aggregates;

import com.primefuel.fulltank.platform.equipment.devicebinding.domain.model.events.DeviceBoundEvent;
import com.primefuel.fulltank.platform.equipment.devicebinding.domain.model.events.DeviceMovedEvent;
import com.primefuel.fulltank.platform.equipment.devicebinding.domain.model.events.DeviceRevokedEvent;
import com.primefuel.fulltank.platform.equipment.devicebinding.domain.model.valueobjects.BindingStatus;
import com.primefuel.fulltank.platform.shared.domain.model.aggregates.AbstractDomainAggregateRoot;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Time-bounded association between a physical device channel and the tank it reports for. The validity
 * window is half-open ({@code [validFrom, validTo)}) and decides attribution of late readings: a payload
 * is attributed by its instant, never by "whatever binding exists now".
 *
 * <p>{@code activeSlot} is 1 while the binding is open and null once closed; the persistence layer
 * declares a unique constraint on (deviceId, channel, activeSlot) so two open bindings per channel are
 * impossible at the database level (a plain UNIQUE index ignores NULLs).
 */
@Getter
@Setter
@NoArgsConstructor
public class DeviceBinding extends AbstractDomainAggregateRoot<DeviceBinding> {

    public static final int OPEN_SLOT = 1;

    private Long id;
    private Long organizationId;
    private String deviceId;
    private String channel;
    private Long tankId;
    private Instant validFrom;
    private Instant validTo;
    private BindingStatus status;
    private Integer activeSlot;

    public DeviceBinding(Long organizationId, String deviceId, String channel, Long tankId, Instant validFrom) {
        if (deviceId == null || deviceId.isBlank()) {
            throw new IllegalArgumentException("A device id is required");
        }
        if (channel == null || channel.isBlank()) {
            throw new IllegalArgumentException("A device channel is required");
        }
        if (tankId == null) {
            throw new IllegalArgumentException("A tank is required");
        }
        if (validFrom == null) {
            throw new IllegalArgumentException("A validity start instant is required");
        }
        this.organizationId = organizationId;
        this.deviceId = deviceId;
        this.channel = channel;
        this.tankId = tankId;
        this.validFrom = validFrom;
        this.status = BindingStatus.ACTIVE;
        this.activeSlot = OPEN_SLOT;
        registerDomainEvent(new DeviceBoundEvent(deviceId, channel, tankId, organizationId, validFrom));
    }

    public boolean isOpen() {
        return validTo == null && status == BindingStatus.ACTIVE;
    }

    /** Half-open interval: the instant {@code validTo} is already outside the binding. */
    public boolean covers(Instant instant) {
        if (instant == null || validFrom == null || instant.isBefore(validFrom)) {
            return false;
        }
        return validTo == null || instant.isBefore(validTo);
    }

    public boolean overlaps(Instant from, Instant to) {
        if (from == null) {
            return false;
        }
        var effectiveTo = to == null ? Instant.MAX : to;
        var thisTo = validTo == null ? Instant.MAX : validTo;
        return validFrom.isBefore(effectiveTo) && from.isBefore(thisTo);
    }

    public void revoke(Instant at) {
        closeAt(at, BindingStatus.REVOKED);
        registerDomainEvent(new DeviceRevokedEvent(deviceId, channel, tankId, organizationId, at));
    }

    /** Closes this period at {@code at} and returns the new binding that continues the device. */
    public DeviceBinding moveTo(Long newTankId, Instant at) {
        if (newTankId == null) {
            throw new IllegalArgumentException("A target tank is required");
        }
        var fromTankId = this.tankId;
        closeAt(at, BindingStatus.CLOSED);
        var next = new DeviceBinding(organizationId, deviceId, channel, newTankId, at);
        registerDomainEvent(new DeviceMovedEvent(deviceId, channel, fromTankId, newTankId, organizationId, at));
        return next;
    }

    private void closeAt(Instant at, BindingStatus targetStatus) {
        if (!isOpen()) {
            throw new IllegalStateException("Only an open binding can be closed");
        }
        if (at == null || at.isBefore(validFrom)) {
            throw new IllegalArgumentException("The closing instant must not precede the validity start");
        }
        this.validTo = at;
        this.status = targetStatus;
        this.activeSlot = null;
    }
}
