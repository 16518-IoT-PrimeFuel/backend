package com.primefuel.fulltank.platform.safety.valve.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ValveCommandRepository extends JpaRepository<ValveCommandEntity, Long> {
    boolean existsByDeliveryIdAndDecisionId(Long deliveryId, Long decisionId);
    List<ValveCommandEntity> findByDeliveryIdAndStatus(Long deliveryId, String status);
    Optional<ValveCommandEntity> findByDeliveryIdAndCommandId(Long deliveryId, String commandId);
}
