package com.primefuel.fulltank.platform.replenishment.application.internal.consumers;

import com.primefuel.fulltank.platform.equipment.api.TankAssets;
import com.primefuel.fulltank.platform.equipment.api.events.TankLevelManuallyUpdatedEvent;
import com.primefuel.fulltank.platform.replenishment.application.commandservices.RefillPolicyCommandService;
import com.primefuel.fulltank.platform.replenishment.domain.model.commands.EvaluateRefillPolicyCommand;
import com.primefuel.fulltank.platform.shared.events.EventInbox;
import com.primefuel.fulltank.platform.telemetry.api.events.ValidatedTankReadingEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Turns a tank level change into a policy evaluation (S09/T09-B). There are two triggers, both converging
 * on the same idempotent evaluation:
 *
 * <ul>
 *   <li>a validated telemetry reading ({@link ValidatedTankReadingEvent});</li>
 *   <li>a manual level edit ({@link TankLevelManuallyUpdatedEvent}), the legacy v1 path — so automatic
 *       replenishment still works without IoT sensors or the frozen {@code devicebinding} infrastructure.</li>
 * </ul>
 *
 * <p>Both use the same {@link EventInbox} consumer and the same reading identity as their dedup key, which
 * also seeds the episode key: retrying (or replaying) a trigger can never open a second episode or generate
 * a second request. Evaluations read the tank snapshot, so out-of-order input cannot regress the level, and
 * they run after the level was applied.
 */
@Service
public class RefillPolicyEvaluationConsumer {

    public static final String CONSUMER = "refill-policy";

    private final EventInbox inbox;
    private final TankAssets tankAssets;
    private final RefillPolicyCommandService refillPolicyCommandService;

    public RefillPolicyEvaluationConsumer(EventInbox inbox,
                                          TankAssets tankAssets,
                                          RefillPolicyCommandService refillPolicyCommandService) {
        this.inbox = inbox;
        this.tankAssets = tankAssets;
        this.refillPolicyCommandService = refillPolicyCommandService;
    }

    @EventListener
    @Order(Ordered.LOWEST_PRECEDENCE)
    @Transactional
    public void on(ValidatedTankReadingEvent event) {
        if (event.tankId() == null) {
            return;
        }
        var sourceKey = telemetrySourceKey(event);
        if (!inbox.consume(CONSUMER, sourceKey)) {
            return;
        }
        evaluate(event.tankId(), sourceKey, event.capturedAt());
    }

    @EventListener
    @Order(Ordered.LOWEST_PRECEDENCE)
    @Transactional
    public void on(TankLevelManuallyUpdatedEvent event) {
        if (event.tankId() == null) {
            return;
        }
        if (!inbox.consume(CONSUMER, event.sourceKey())) {
            return;
        }
        evaluate(event.tankId(), event.sourceKey(), event.observedAt());
    }

    private void evaluate(Long tankId, String episodeKeySeed, Instant evaluatedAt) {
        var snapshot = tankAssets.findById(tankId).orElse(null);
        if (snapshot == null || !snapshot.active()) {
            return;
        }
        refillPolicyCommandService.handle(new EvaluateRefillPolicyCommand(
                snapshot.id(),
                snapshot.organizationId(),
                snapshot.customerAccountId(),
                snapshot.currentLevel(),
                snapshot.unit(),
                snapshot.capacity(),
                episodeKeySeed,
                evaluatedAt));
    }

    static String telemetrySourceKey(ValidatedTankReadingEvent event) {
        return event.deviceId() + ":" + event.channel() + ":" + event.sequence();
    }
}
