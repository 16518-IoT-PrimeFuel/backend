package com.primefuel.fulltank.platform.replenishment.domain.model.aggregates;

import com.primefuel.fulltank.platform.replenishment.domain.model.commands.ConfigureRefillPolicyCommand;
import com.primefuel.fulltank.platform.replenishment.domain.model.valueobjects.RefillThresholds;
import com.primefuel.fulltank.platform.shared.domain.model.aggregates.AbstractDomainAggregateRoot;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Per-tank configuration of the low-level policy. Absent means "use the approved global defaults";
 * present means an explicit override. The version is bumped on every reconfiguration so an episode can
 * record which policy produced it.
 */
@Getter
@Setter
@NoArgsConstructor
public class RefillPolicy extends AbstractDomainAggregateRoot<RefillPolicy> {

    private Long id;
    private Long tankId;
    private Long organizationId;
    private double lowLevelPercent;
    private double hysteresisPercent;
    private double targetLevelPercent;
    private Long providerId;
    private Long fuelProductId;
    private boolean autoGenerateEnabled;
    private int policyVersion;
    private int version;

    public RefillPolicy(ConfigureRefillPolicyCommand command) {
        if (command.tankId() == null) {
            throw new IllegalArgumentException("A tank is required");
        }
        if (command.organizationId() == null) {
            throw new IllegalArgumentException("An organization is required");
        }
        apply(command);
        this.policyVersion = 1;
        this.version = 0;
    }

    public void reconfigure(ConfigureRefillPolicyCommand command) {
        apply(command);
        this.policyVersion = this.policyVersion + 1;
    }

    public RefillThresholds thresholds() {
        return new RefillThresholds(lowLevelPercent, hysteresisPercent);
    }

    /**
     * Continuous generation is opt-in per tank and only possible once the product and provider are
     * configured. Until then the policy evaluates and records decisions in shadow mode only.
     */
    public boolean canGenerateRequests() {
        return autoGenerateEnabled && providerId != null && fuelProductId != null;
    }

    private void apply(ConfigureRefillPolicyCommand command) {
        var thresholds = new RefillThresholds(
                command.lowLevelPercent() == null
                        ? RefillThresholds.DEFAULT_LOW_LEVEL_PERCENT : command.lowLevelPercent(),
                command.hysteresisPercent() == null
                        ? RefillThresholds.DEFAULT_HYSTERESIS_PERCENT : command.hysteresisPercent());
        this.lowLevelPercent = thresholds.lowLevelPercent();
        this.hysteresisPercent = thresholds.hysteresisPercent();
        this.targetLevelPercent = command.targetLevelPercent() == null ? 100.0 : command.targetLevelPercent();
        if (this.targetLevelPercent <= 0 || this.targetLevelPercent > 100) {
            throw new IllegalArgumentException("Target level percent must be between 0 and 100");
        }
        this.providerId = command.providerId();
        this.fuelProductId = command.fuelProductId();
        this.autoGenerateEnabled = Boolean.TRUE.equals(command.autoGenerateEnabled());
    }
}
