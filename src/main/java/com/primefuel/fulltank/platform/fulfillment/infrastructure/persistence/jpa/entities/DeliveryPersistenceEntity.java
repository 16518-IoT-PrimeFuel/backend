package com.primefuel.fulltank.platform.fulfillment.infrastructure.persistence.jpa.entities;

import com.primefuel.fulltank.platform.fulfillment.domain.model.valueobjects.DeliveryPhysicalState;
import com.primefuel.fulltank.platform.fulfillment.domain.model.valueobjects.DeliveryStatus;
import com.primefuel.fulltank.platform.shared.infrastructure.persistence.jpa.entities.AuditableAbstractPersistenceEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(
        name = "deliveries",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_deliveries_assignment_command_id", columnNames = "assignment_command_id"),
                @UniqueConstraint(name = "uk_deliveries_active_order_id", columnNames = "active_order_id")
        })
@Getter
@Setter
@NoArgsConstructor
public class DeliveryPersistenceEntity extends AuditableAbstractPersistenceEntity {

    @Column(nullable = false)
    private Long orderId;

    /**
     * The order while this delivery still holds it; null once FAILED/CANCELLED. Unique, so an order has one
     * live delivery yet a failed one can be reassigned (NULLs never collide on MySQL or H2).
     */
    @Column(name = "active_order_id")
    private Long activeOrderId;

    @Column(nullable = false)
    private Long providerId;

    @Column(nullable = false)
    private Long driverId;

    @Column(nullable = false)
    private Long vehicleId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeliveryStatus status;

    private Instant dispatchedAt;

    private Instant deliveredAt;

    private String scheduledDate;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(name = "physical_state", length = 30)
    private DeliveryPhysicalState physicalState;

    private Instant startedAt;

    private Instant arrivedAt;

    private Instant deliveringAt;

    private Double requestedVolume;

    private Double deliveredVolume;

    @Version
    @Column(nullable = false)
    private int version;

    @Column(name = "assignment_command_id", length = 120)
    private String assignmentCommandId;
}
