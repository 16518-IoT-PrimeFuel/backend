package com.primefuel.fulltank.platform.replenishment.api;

import com.primefuel.fulltank.platform.shared.application.result.*;

public interface TankRefillConfiguration {
    Result<Long, ApplicationError> configure(
            Long tankId,
            Long organizationId,
            double lowLevelPercent,
            double hysteresisPercent,
            double targetLevelPercent,
            Long providerId,
            Long productId,
            boolean autoGenerateEnabled);
}
