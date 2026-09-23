package com.primefuel.fulltank.platform.ordering.application.internal.consumers;

import com.primefuel.fulltank.platform.ordering.domain.repositories.FuelOrderRepository;
import com.primefuel.fulltank.platform.shared.events.EventEnvelope;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * S23/T23-B compatibility adapter, owned by {@code ordering}. Before T23-B the {@code payment} module wrote
 * {@code ordering} directly (it imported {@link FuelOrderRepository} and called {@code markPaid}). Now
 * {@code payment} only publishes {@code payment.completed.v1}; this adapter — which owns the order aggregate
 * — reacts and marks the order paid, so the v1 contract (the order reaches {@code PAID}) is preserved without
 * payment knowing anything about ordering's persistence.
 *
 * <p>It runs synchronously inside the publisher's transaction, so a rejected order (cancelled) still fails the
 * completion as before.
 */
@Service
public class OrderingPaymentCompletionAdapter {

    private static final String EVENT_TYPE = "payment.completed.v1";
    private static final Pattern ORDER_ID = Pattern.compile("\"orderId\"\\s*:\\s*(\\d+)");

    private final FuelOrderRepository orderRepository;

    public OrderingPaymentCompletionAdapter(FuelOrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @EventListener
    @Order(Ordered.LOWEST_PRECEDENCE)
    @Transactional
    public void on(EventEnvelope envelope) {
        if (!EVENT_TYPE.equals(envelope.eventType())) {
            return;
        }
        var orderId = orderIdOf(envelope.payload());
        if (orderId == null) {
            return;
        }
        orderRepository.findById(orderId).ifPresent(order -> {
            order.markPaid();
            orderRepository.save(order);
        });
    }

    private static Long orderIdOf(String payload) {
        if (payload == null) {
            return null;
        }
        Matcher matcher = ORDER_ID.matcher(payload);
        return matcher.find() ? Long.valueOf(matcher.group(1)) : null;
    }
}
