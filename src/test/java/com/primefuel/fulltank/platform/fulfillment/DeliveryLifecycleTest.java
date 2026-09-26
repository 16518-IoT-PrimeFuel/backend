package com.primefuel.fulltank.platform.fulfillment;

import com.primefuel.fulltank.platform.fulfillment.domain.model.aggregates.Delivery;
import com.primefuel.fulltank.platform.fulfillment.domain.model.commands.CreateDeliveryCommand;
import com.primefuel.fulltank.platform.fulfillment.domain.model.valueobjects.DeliveryStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DeliveryLifecycleTest {

    @Test
    void arrivalMustFollowDispatchAndCompletionAcceptsArrival() {
        var delivery = new Delivery(new CreateDeliveryCommand(1L, 2L, 3L, 4L, "2026-09-25", null));

        assertThrows(IllegalStateException.class, delivery::arrive);
        delivery.dispatch();
        delivery.arrive();
        delivery.complete();

        assertEquals(DeliveryStatus.DELIVERED, delivery.getStatus());
        assertThrows(IllegalStateException.class, delivery::complete);
    }
}
