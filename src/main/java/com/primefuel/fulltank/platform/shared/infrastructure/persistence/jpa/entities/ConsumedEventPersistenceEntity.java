package com.primefuel.fulltank.platform.shared.infrastructure.persistence.jpa.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "consumed_events",
        uniqueConstraints = @UniqueConstraint(name = "uk_consumed_events_consumer_event", columnNames = {"consumer", "event_id"}))
@Getter
@Setter
@NoArgsConstructor
public class ConsumedEventPersistenceEntity extends AuditableAbstractPersistenceEntity {

    @Column(nullable = false, length = 80)
    private String consumer;

    @Column(name = "event_id", nullable = false, length = 36)
    private String eventId;
}
