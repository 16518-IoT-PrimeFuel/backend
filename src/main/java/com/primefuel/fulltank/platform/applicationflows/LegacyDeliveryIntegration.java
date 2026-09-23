package com.primefuel.fulltank.platform.applicationflows;

import com.primefuel.fulltank.platform.fulfillment.api.DeliveryIntegration;
import com.primefuel.fulltank.platform.shared.application.result.ApplicationError;
import com.primefuel.fulltank.platform.shared.application.result.Result;
import org.springframework.stereotype.Component;

/**
 * Adapter for the fulfillment-owned {@link DeliveryIntegration} port, implemented in the composition root
 * so {@code fulfillment} can drive the legacy v1 delivery side effects without importing any foreign
 * repository. It is intentionally <strong>not</strong> transactional: the real work happens in
 * {@link LegacyDeliveryExecutor}, and this wrapper only turns the executor's throw-to-rollback signal back
 * into a {@code Result} (so a failure never leaves a partially applied assignment).
 */
@Component("deliveryIntegration")
public class LegacyDeliveryIntegration implements DeliveryIntegration {

    private final LegacyDeliveryExecutor executor;

    public LegacyDeliveryIntegration(LegacyDeliveryExecutor executor) {
        this.executor = executor;
    }

    @Override
    public Result<Long, ApplicationError> createDelivery(CreateLegacyDeliveryCommand command) {
        try {
            return Result.success(executor.createDelivery(command));
        } catch (AssignmentFailedException failure) {
            return Result.failure(failure.error());
        }
    }

    @Override
    public void applyCompletionEffects(CompletionEffectsCommand command) {
        executor.applyCompletionEffects(command);
    }
}
