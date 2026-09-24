package com.primefuel.fulltank.platform.fulfillment.infrastructure.persistence.jpa.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "valve_commands")
@Getter
@Setter
@NoArgsConstructor
public class ValveCommandPersistenceEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "command_id", nullable = false, unique = true, length = 160)
    private String commandId;

    @Column(name = "delivery_id", nullable = false)
    private Long deliveryId;

    @Column(name = "desired_state", nullable = false, length = 20)
    private String desiredState;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "ack_id", unique = true, length = 160)
    private String ackId;

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;
}
