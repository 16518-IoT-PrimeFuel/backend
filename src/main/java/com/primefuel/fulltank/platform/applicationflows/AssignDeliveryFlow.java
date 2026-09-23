package com.primefuel.fulltank.platform.applicationflows;

import com.primefuel.fulltank.platform.shared.application.result.ApplicationError;
import com.primefuel.fulltank.platform.shared.application.result.Result;
import org.springframework.stereotype.Service;

/**
 * The S15/T15-A assignment orchestrator, public entry point. It lives in {@code applicationflows} — the
 * composition root — and unites acceptance, reservations and delivery behind public seams:
 * {@code ConsumeAcceptance} → {@code ReserveSupply} → {@code ReserveFleet} → create the assigned delivery.
 *
 * <p>All the work (and the single local transaction) happens in {@link AssignDeliveryExecutor}; this class
 * only adapts the executor's throw-to-rollback signal back into a {@code Result}, so callers get an
 * {@link ApplicationError} (mapped to the right HTTP status by the controller) while the transaction is
 * guaranteed to have rolled back everything on failure.
 *
 * <p>Assigning is not starting: the delivery is created in {@code ASSIGNED}; the physical machine (T14-A)
 * owns start/arrive/complete. The acceptance behind the order must be {@code ACCEPTED} and is consumed
 * <em>once</em>; a retry with the same {@code commandId} returns the same delivery.
 */
@Service
public class AssignDeliveryFlow {

    private final AssignDeliveryExecutor executor;

    public AssignDeliveryFlow(AssignDeliveryExecutor executor) {
        this.executor = executor;
    }

    public Result<AssignDeliveryResult, ApplicationError> assign(AssignDeliveryFlowCommand command) {
        try {
            return Result.success(executor.execute(command));
        } catch (AssignmentFailedException failure) {
            return Result.failure(failure.error());
        }
    }
}
