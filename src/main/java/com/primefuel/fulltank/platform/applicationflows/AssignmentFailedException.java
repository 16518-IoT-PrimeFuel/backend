package com.primefuel.fulltank.platform.applicationflows;

import com.primefuel.fulltank.platform.shared.application.result.ApplicationError;

/**
 * Internal control-flow signal of the assignment orchestrator: a step failed with a domain
 * {@link ApplicationError}. It is thrown (never returned) so the surrounding transaction rolls back
 * <em>everything</em> — the consumed acceptance, the supply hold and the fleet hold included — instead of
 * committing a partially applied assignment. The non-transactional entry point catches it and turns it back
 * into a {@code Result.failure}.
 */
class AssignmentFailedException extends RuntimeException {

    private final transient ApplicationError error;

    AssignmentFailedException(ApplicationError error) {
        super(error.message());
        this.error = error;
    }

    ApplicationError error() {
        return error;
    }
}
