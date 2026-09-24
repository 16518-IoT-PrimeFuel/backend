package com.primefuel.fulltank.platform.ordering.infrastructure.persistence.jpa.entities;

import com.primefuel.fulltank.platform.shared.infrastructure.persistence.jpa.entities.AuditableAbstractPersistenceEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "replenishment_request_lifecycle")
@Getter
@Setter
@NoArgsConstructor
public class ReplenishmentLifecyclePersistenceEntity extends AuditableAbstractPersistenceEntity {
    @Column(name = "request_id", nullable = false, unique = true) private Long requestId;
    @Column(name = "idempotency_key", nullable = false, unique = true, length = 180) private String idempotencyKey;
    @Column(nullable = false, length = 20) private String state;
    @Column(nullable = false) private long version;
    @Column(name = "consumed_order_id") private Long consumedOrderId;
    @Column(name = "consumed_at") private Instant consumedAt;
}
