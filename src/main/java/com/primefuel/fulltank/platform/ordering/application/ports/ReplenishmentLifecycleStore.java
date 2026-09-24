package com.primefuel.fulltank.platform.ordering.application.ports;

import java.util.Optional;

public interface ReplenishmentLifecycleStore {
    Optional<Long> findRequestIdByIdempotencyKey(String idempotencyKey);

    void create(Long requestId, String idempotencyKey);

    boolean transition(Long requestId, String expectedState, String nextState);

    boolean consume(Long requestId, Long orderId);
}
