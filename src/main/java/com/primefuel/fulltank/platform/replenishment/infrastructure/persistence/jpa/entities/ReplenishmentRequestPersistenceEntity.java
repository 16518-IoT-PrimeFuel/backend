package com.primefuel.fulltank.platform.replenishment.infrastructure.persistence.jpa.entities;

import com.primefuel.fulltank.platform.replenishment.domain.model.valueobjects.ReplenishmentSource;
import com.primefuel.fulltank.platform.replenishment.domain.model.valueobjects.ReplenishmentStatus;
import com.primefuel.fulltank.platform.shared.infrastructure.persistence.jpa.entities.AuditableAbstractPersistenceEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
        name = "replenishment_requests",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_replenishment_requests_episode_key", columnNames = "episode_key"))
@Getter
@Setter
@NoArgsConstructor
public class ReplenishmentRequestPersistenceEntity extends AuditableAbstractPersistenceEntity {

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(name = "customer_account_id")
    private Long customerAccountId;

    @Column(name = "tank_id")
    private Long tankId;

    @Column(name = "provider_id", nullable = false)
    private Long providerId;

    @Column(name = "fuel_product_id", nullable = false)
    private Long fuelProductId;

    @Column(nullable = false)
    private double quantity;

    @Column(nullable = false, length = 20)
    private String unit;

    @Column(name = "unit_price", nullable = false)
    private double unitPrice;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20, columnDefinition = "VARCHAR(20)")
    private ReplenishmentStatus status;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20, columnDefinition = "VARCHAR(20)")
    private ReplenishmentSource source;

    @Column(name = "episode_key", length = 120)
    private String episodeKey;

    @Column(name = "rejection_reason", length = 240)
    private String rejectionReason;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "acceptance_consumed", nullable = false)
    private boolean acceptanceConsumed;

    @Version
    @Column(nullable = false)
    private int version;
}
