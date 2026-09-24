package com.primefuel.fulltank.platform.ordering.infrastructure.persistence.jpa.repositories;

import com.primefuel.fulltank.platform.ordering.infrastructure.persistence.jpa.entities.ReplenishmentLifecyclePersistenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface ReplenishmentLifecycleJpaRepository extends JpaRepository<ReplenishmentLifecyclePersistenceEntity, Long> {
    Optional<ReplenishmentLifecyclePersistenceEntity> findByRequestId(Long requestId);

    Optional<ReplenishmentLifecyclePersistenceEntity> findByIdempotencyKey(String idempotencyKey);

    @Modifying
    @Query("update ReplenishmentLifecyclePersistenceEntity l set l.state = :nextState, l.version = l.version + 1 "
            + "where l.requestId = :requestId and l.state = :expectedState")
    int transition(@Param("requestId") Long requestId, @Param("expectedState") String expectedState,
                   @Param("nextState") String nextState);

    @Modifying
    @Query("update ReplenishmentLifecyclePersistenceEntity l set l.state = 'CONSUMED', "
            + "l.consumedOrderId = :orderId, l.consumedAt = :consumedAt, l.version = l.version + 1 "
            + "where l.requestId = :requestId and l.state = 'ACCEPTED' and l.consumedOrderId is null")
    int consume(@Param("requestId") Long requestId, @Param("orderId") Long orderId,
                @Param("consumedAt") Instant consumedAt);
}
