package com.primefuel.fulltank.platform.fulfillment.domain.repositories;

import com.primefuel.fulltank.platform.fulfillment.domain.model.aggregates.Delivery;

import java.util.List;
import java.util.Optional;

public interface DeliveryRepository {
    Optional<Delivery> findById(Long id);
    Optional<Delivery> findByOrderId(Long orderId);

    /** The delivery created by an orchestrated assignment command (T15-A idempotency), if any. */
    Optional<Delivery> findByAssignmentCommandId(String assignmentCommandId);
    List<Delivery> findByProviderId(Long providerId);
    List<Delivery> findAll();
    Delivery save(Delivery delivery);

    /** Flushes immediately so optimistic-lock conflicts surface inside the caller's transaction. */
    Delivery saveAndFlush(Delivery delivery);
}
