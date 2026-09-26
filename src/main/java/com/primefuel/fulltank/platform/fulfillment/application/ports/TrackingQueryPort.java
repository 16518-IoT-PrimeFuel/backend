package com.primefuel.fulltank.platform.fulfillment.application.ports;

import java.util.List;
import java.util.Optional;

public interface TrackingQueryPort {
    List<TrackingPointData> findByDeliveryId(Long deliveryId);
    Optional<TrackingPointData> findLatestByDeliveryId(Long deliveryId);
}
