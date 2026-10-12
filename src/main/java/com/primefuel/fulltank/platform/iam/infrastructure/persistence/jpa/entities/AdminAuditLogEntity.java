package com.primefuel.fulltank.platform.iam.infrastructure.persistence.jpa.entities;

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
@Table(name = "admin_audit_log")
@Getter
@Setter
@NoArgsConstructor
public class AdminAuditLogEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actor_user_id", nullable = false)
    private Long actorUserId;

    @Column(nullable = false, length = 20)
    private String action;

    @Column(name = "target_user_id", nullable = false)
    private Long targetUserId;

    @Column(name = "at", nullable = false)
    private Instant at;

    public AdminAuditLogEntity(Long actorUserId, String action, Long targetUserId, Instant at) {
        this.actorUserId = actorUserId;
        this.action = action;
        this.targetUserId = targetUserId;
        this.at = at;
    }
}
