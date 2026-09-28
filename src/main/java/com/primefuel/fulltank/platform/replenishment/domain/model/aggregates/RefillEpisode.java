package com.primefuel.fulltank.platform.replenishment.domain.model.aggregates;

import com.primefuel.fulltank.platform.replenishment.domain.model.valueobjects.RefillEpisodeStatus;
import com.primefuel.fulltank.platform.shared.domain.model.aggregates.AbstractDomainAggregateRoot;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * One low-level episode for a tank. The episode, not the reading, is the unit of "a need exists": while
 * it stays open its low readings are suppressed, which is what turns a hundred low readings into a
 * single request. It closes (re-arms) only after the level recovers past the hysteresis band, and a
 * rejected request can therefore never open a loop.
 */
@Getter
@Setter
@NoArgsConstructor
public class RefillEpisode extends AbstractDomainAggregateRoot<RefillEpisode> {

    private Long id;
    private String episodeKey;
    private Long tankId;
    private Long organizationId;
    private int policyVersion;
    private RefillEpisodeStatus status;
    private Instant openedAt;
    private double openedLevelPercent;
    private double openedLevel;
    private double targetLevel;
    private double requestedVolume;
    private String unit;
    private boolean requestEmitted;
    private Long requestId;
    private Instant closedAt;
    private Double closedLevelPercent;
    private int version;

    public RefillEpisode(String episodeKey,
                         Long tankId,
                         Long organizationId,
                         int policyVersion,
                         double openedLevelPercent,
                         double openedLevel,
                         double targetLevel,
                         String unit,
                         Instant openedAt) {
        if (episodeKey == null || episodeKey.isBlank()) {
            throw new IllegalArgumentException("An episode key is required");
        }
        if (tankId == null) {
            throw new IllegalArgumentException("A tank is required");
        }
        if (openedAt == null) {
            throw new IllegalArgumentException("An opening instant is required");
        }
        var requestedVolume = targetLevel - openedLevel;
        if (requestedVolume <= 0) {
            throw new IllegalArgumentException("The requested volume must be positive");
        }
        this.episodeKey = episodeKey;
        this.tankId = tankId;
        this.organizationId = organizationId;
        this.policyVersion = policyVersion;
        this.status = RefillEpisodeStatus.OPEN;
        this.openedAt = openedAt;
        this.openedLevelPercent = openedLevelPercent;
        this.openedLevel = openedLevel;
        this.targetLevel = targetLevel;
        this.requestedVolume = requestedVolume;
        this.unit = unit;
        this.requestEmitted = false;
        this.version = 0;
    }

    public boolean isOpen() {
        return status == RefillEpisodeStatus.OPEN;
    }

    /** Records the request produced by this episode, once. */
    public void markRequestEmitted(Long requestId) {
        if (!isOpen()) {
            throw new IllegalStateException("Only an open episode can emit a request");
        }
        this.requestEmitted = true;
        this.requestId = requestId;
    }

    /** Recovery past the hysteresis band: the episode closes and the policy becomes armed again. */
    public void rearm(double closedLevelPercent, Instant closedAt) {
        if (!isOpen()) {
            throw new IllegalStateException("Only an open episode can be re-armed");
        }
        this.status = RefillEpisodeStatus.REARMED;
        this.closedLevelPercent = closedLevelPercent;
        this.closedAt = closedAt;
    }
}
