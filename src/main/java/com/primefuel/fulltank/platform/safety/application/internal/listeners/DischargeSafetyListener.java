package com.primefuel.fulltank.platform.safety.application.internal.listeners;

import com.primefuel.fulltank.platform.safety.api.GeofenceEvaluation;
import com.primefuel.fulltank.platform.shared.events.EventEnvelope;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** S17/T17-B synchronous consumer; fulfillment publishes to avoid a fulfillment ↔ safety module cycle. */
@Component
public class DischargeSafetyListener {

    private static final String EVENT_TYPE = "delivery.discharge.started.v1";

    private final GeofenceEvaluation evaluation;

    public DischargeSafetyListener(GeofenceEvaluation evaluation) {
        this.evaluation = evaluation;
    }

    @EventListener
    @Transactional
    public void on(EventEnvelope event) {
        if (!EVENT_TYPE.equals(event.eventType())) {
            return;
        }
        var result = evaluation.evaluate(Long.valueOf(event.aggregateId()));
        if (result.isFailure()) {
            throw new IllegalStateException("Safety evaluation did not produce a persisted decision");
        }
        // AUTHORIZED, BLOCKED and NO_POLICY are detection-only outcomes; none changes delivery state.
    }
}
