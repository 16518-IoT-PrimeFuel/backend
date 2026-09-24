package com.primefuel.fulltank.platform.equipment.infrastructure.persistence.jpa.entities;

import com.primefuel.fulltank.platform.shared.infrastructure.persistence.jpa.entities.AuditableAbstractPersistenceEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "refill_episodes")
@Getter
@Setter
@NoArgsConstructor
public class RefillEpisodePersistenceEntity extends AuditableAbstractPersistenceEntity {
    @Column(name = "episode_key", nullable = false, unique = true, length = 180) private String episodeKey;
    @Column(name = "tank_id", nullable = false) private Long tankId;
    @Column(name = "started_at", nullable = false) private Instant startedAt;
    @Column(name = "closed_at") private Instant closedAt;
    @Column(nullable = false, length = 20) private String status;
    @Column(name = "request_id") private Long requestId;
}
