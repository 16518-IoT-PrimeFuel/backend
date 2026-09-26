package com.primefuel.fulltank.platform.fulfillment.infrastructure.persistence.jpa.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;

@Entity
@Table(name = "delivery_business_journal",
        uniqueConstraints = @UniqueConstraint(name = "uk_delivery_business_journal_source_event", columnNames = "source_event_id"),
        indexes = @Index(name = "ix_delivery_business_journal_delivery_time", columnList = "delivery_id,occurred_at"))
@Getter @Setter @NoArgsConstructor
public class DeliveryBusinessJournalEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "source_event_id", nullable = false, length = 36) private String sourceEventId;
    @Column(name = "delivery_id", nullable = false) private Long deliveryId;
    @Column(name = "provider_id", nullable = false) private Long providerId;
    @Column(nullable = false, length = 30) private String type;
    @Column(name = "occurred_at", nullable = false) private Instant occurredAt;
    @Column(nullable = false, length = 200) private String summary;
    @Column(name = "ref_id", nullable = false, length = 80) private String refId;
}
