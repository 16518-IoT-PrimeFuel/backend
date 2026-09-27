package com.primefuel.fulltank.platform.applicationflows;

import com.primefuel.fulltank.platform.replenishment.domain.model.aggregates.ReplenishmentRequest;
import com.primefuel.fulltank.platform.shared.application.result.ApplicationError;
import com.primefuel.fulltank.platform.shared.application.result.Result;
import org.springframework.stereotype.Service;

@Service
public class ReplenishmentAcceptanceFlow {

    private final ReplenishmentAcceptanceExecutor executor;

    public ReplenishmentAcceptanceFlow(ReplenishmentAcceptanceExecutor executor) {
        this.executor = executor;
    }

    public Result<ReplenishmentRequest, ApplicationError> accept(Long requestId) {
        try {
            return Result.success(executor.execute(requestId));
        } catch (AssignmentFailedException failure) {
            return Result.failure(failure.error());
        }
    }
}
