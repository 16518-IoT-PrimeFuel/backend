package com.primefuel.fulltank.platform.ordering.api;

import java.util.Optional;

/**
 * Public read seam over fuel orders (S23/T23-B). Other modules read an order's commercial snapshot through
 * this interface — they must not depend on {@code ordering.domain..} (which holds the query records and the
 * aggregate). Introduced so {@code payment} can validate its invariants without importing a domain type.
 */
public interface OrderLookup {

    Optional<OrderSnapshot> findById(Long orderId);

    record OrderSnapshot(
            Long id,
            Long requestId,
            Long companyId,
            Long providerId,
            Long fuelProductId,
            Long equipmentId,
            Double requestedQuantity,
            Double totalPrice,
            String status) {
    }
}
