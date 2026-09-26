package com.primefuel.fulltank.platform.fulfillment.application.ports;

import java.util.List;

public interface JournalQueryPort {
    List<JournalEntryData> findByDeliveryId(Long deliveryId);
}
