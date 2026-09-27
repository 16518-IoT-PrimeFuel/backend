package com.primefuel.fulltank.platform.applicationflows;

import com.primefuel.fulltank.platform.shared.application.result.ApplicationError;

class ReplenishmentAcceptanceFailedException extends RuntimeException {
    private final ApplicationError error;

    ReplenishmentAcceptanceFailedException(ApplicationError error) {
        super(error.message());
        this.error = error;
    }

    ApplicationError error() {
        return error;
    }
}
