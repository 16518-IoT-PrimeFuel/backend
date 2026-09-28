package com.primefuel.fulltank.platform.replenishment.infrastructure.persistence.jpa.entities;

import com.primefuel.fulltank.platform.replenishment.domain.model.valueobjects.RefillEpisodeStatus;
import com.primefuel.fulltank.platform.shared.infrastructure.persistence.jpa.entities.AuditableAbstractPersistenceEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(
        name = "refill_episodes",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_refill_episodes_episode_key", columnNames = "episode_key"),
                @UniqueConstraint(
                        name = "uk_refill_episodes_open_slot", columnNames = "open_slot")})
@Getter
@Setter
@NoArgsConstructor
public class RefillEpisodePersistenceEntity extends AuditableAbstractPersistenceEntity {

    @Column(name = "episode_key", nullable = false, length = 160)
    private String episodeKey;

    @Column(name = "tank_id", nullable = false)
    private Long tankId;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(name = "policy_version", nullable = false)
    private int policyVersion;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20, columnDefinition = "VARCHAR(20)")
    private RefillEpisodeStatus status;

    @Column(name = "opened_at", nullable = false)
    private Instant openedAt;

    @Column(name = "opened_level_percent", nullable = false)
    private double openedLevelPercent;

    @Column(name = "opened_level", nullable = false)
    private double openedLevel;

    @Column(name = "target_level", nullable = false)
    private double targetLevel;

    @Column(name = "requested_volume", nullable = false)
    private double requestedVolume;

    @Column(nullable = false, length = 20)
    private String unit;

    @Column(name = "request_emitted", nullable = false)
    private boolean requestEmitted;

    @Column(name = "request_id")
    private Long requestId;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "closed_level_percent")
    private Double closedLevelPercent;

    /** Equals the tank id while the episode is OPEN and null once re-armed: the unique barrier. */
    @Column(name = "open_slot")
    private Long openSlot;

    @Version
    @Column(nullable = false)
    private int version;
}
