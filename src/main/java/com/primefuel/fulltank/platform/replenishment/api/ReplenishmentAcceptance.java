package com.primefuel.fulltank.platform.replenishment.api;

import com.primefuel.fulltank.platform.shared.application.result.ApplicationError;
import com.primefuel.fulltank.platform.shared.application.result.Result;

/**
 * Public write seam over a replenishment request's acceptance (S15/T15-A). The orchestrator consumes the
 * acceptance <em>once</em> before it turns a request into a delivery: a second consume of the same request
 * is refused so no request can be assigned twice. See {@code ReplenishmentLookup} for reads.
 */
public interface ReplenishmentAcceptance {

    /**
     * Consumes the acceptance of an accepted request. Returns {@code true} the first time, {@code false} if
     * it was already consumed; an unknown request or one that is not {@code ACCEPTED} is an error
     * ({@code * _NOT_FOUND} / {@code * _CONFLICT}).
     */
    Result<Boolean, ApplicationError> consume(Long requestId);
}
