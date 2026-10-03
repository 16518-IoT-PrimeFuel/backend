package com.primefuel.fulltank.platform.replenishment.infrastructure.services;

import com.primefuel.fulltank.platform.replenishment.api.TankRefillConfiguration;
import com.primefuel.fulltank.platform.replenishment.application.commandservices.RefillPolicyCommandService;
import com.primefuel.fulltank.platform.replenishment.domain.model.commands.ConfigureRefillPolicyCommand;
import com.primefuel.fulltank.platform.shared.application.result.*;
import com.primefuel.fulltank.platform.shared.application.result.Result;

import org.springframework.stereotype.Component;

@Component
public class TankRefillConfigurationImpl implements TankRefillConfiguration {
    private final RefillPolicyCommandService policies;

    public TankRefillConfigurationImpl(RefillPolicyCommandService policies) {
        this.policies = policies;
    }

    public Result<Long, ApplicationError> configure(
            Long tankId,
            Long organizationId,
            double lowLevelPercent,
            double hysteresisPercent,
            double targetLevelPercent,
            Long providerId,
            Long productId,
            boolean autoGenerateEnabled) {
        var result =
                policies.handle(
                        new ConfigureRefillPolicyCommand(
                                tankId,
                                organizationId,
                                lowLevelPercent,
                                hysteresisPercent,
                                targetLevelPercent,
                                providerId,
                                productId,
                                autoGenerateEnabled));
        return switch (result) {
            case Result.Success<?, ?> ignored -> Result.success(tankId);
            case Result.Failure<?, ?> failure -> Result.failure((ApplicationError) failure.error());
        };
    }
}
