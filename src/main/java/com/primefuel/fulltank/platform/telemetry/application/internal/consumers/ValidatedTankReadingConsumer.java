package com.primefuel.fulltank.platform.telemetry.application.internal.consumers;

import com.primefuel.fulltank.platform.equipment.api.TankAssets;
import com.primefuel.fulltank.platform.shared.events.EventInbox;
import com.primefuel.fulltank.platform.telemetry.api.events.ValidatedTankReadingEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies a validated reading to its tank. Dedup and application happen in the **same transaction**: the
 * inbox row only commits if the tank was updated, so a crash before commit leaves the reading pending and
 * a retry re-applies it (nothing is silently lost, nothing is applied twice).
 *
 * <p>Out-of-order readings are accepted by the inbox but ignored by the tank, which keeps the newest
 * snapshot. It runs before any policy listener so downstream consumers observe the updated snapshot.
 */
@Service
public class ValidatedTankReadingConsumer {

    public static final String CONSUMER = "tank-reading";

    private final EventInbox inbox;
    private final TankAssets tankAssets;

    public ValidatedTankReadingConsumer(EventInbox inbox, TankAssets tankAssets) {
        this.inbox = inbox;
        this.tankAssets = tankAssets;
    }

    @EventListener
    @Order(Ordered.HIGHEST_PRECEDENCE)
    @Transactional
    public void on(ValidatedTankReadingEvent event) {
        if (event.tankId() == null) {
            return;
        }
        if (!inbox.consume(CONSUMER, eventId(event))) {
            // Already applied: a replay changes nothing.
            return;
        }
        tankAssets.applyValidatedReading(event.tankId(), event.level(), event.unit(), event.capturedAt());
    }

    static String eventId(ValidatedTankReadingEvent event) {
        return event.deviceId() + ":" + event.channel() + ":" + event.sequence();
    }
}
