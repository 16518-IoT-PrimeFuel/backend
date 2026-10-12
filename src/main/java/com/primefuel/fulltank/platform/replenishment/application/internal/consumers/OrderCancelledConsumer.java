package com.primefuel.fulltank.platform.replenishment.application.internal.consumers;

import com.primefuel.fulltank.platform.replenishment.domain.model.aggregates.RefillEpisode;
import com.primefuel.fulltank.platform.replenishment.domain.repositories.RefillEpisodeRepository;
import com.primefuel.fulltank.platform.replenishment.domain.repositories.ReplenishmentRequestRepository;
import com.primefuel.fulltank.platform.shared.events.EventEnvelope;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.regex.Pattern;

/**
 * Keeps the request coherent when its order is cancelled: the accepted request is closed as CANCELLED, and the
 * refill episode it came from is closed the same way a rejected or cancelled request closes it (US-17), so the
 * policy can generate a new request if the tank is still low. Runs inside the publisher's transaction, like the
 * ordering payment adapter.
 */
@Service
public class OrderCancelledConsumer {

    private static final String EVENT_TYPE = "order.cancelled.v1";
    private static final Pattern ORDER_ID = Pattern.compile("\"orderId\"\\s*:\\s*(\\d+)");

    private final ReplenishmentRequestRepository repository;
    private final RefillEpisodeRepository episodes;

    public OrderCancelledConsumer(ReplenishmentRequestRepository repository, RefillEpisodeRepository episodes) {
        this.repository = repository;
        this.episodes = episodes;
    }

    @EventListener
    @Order(Ordered.LOWEST_PRECEDENCE)
    @Transactional
    public void on(EventEnvelope envelope) {
        if (!EVENT_TYPE.equals(envelope.eventType()) || envelope.payload() == null) {
            return;
        }
        var matcher = ORDER_ID.matcher(envelope.payload());
        if (!matcher.find()) {
            return;
        }
        repository.findByOrderId(Long.valueOf(matcher.group(1))).ifPresent(request -> {
            if (request.cancelForOrder()) {
                repository.save(request);
                closeEpisodeOf(request.getEpisodeKey());
            }
        });
    }

    private void closeEpisodeOf(String episodeKey) {
        if (episodeKey == null) {
            return;
        }
        episodes.findByEpisodeKey(episodeKey)
                .filter(RefillEpisode::isOpen)
                .ifPresent(episode -> {
                    episode.closeByRequest(Instant.now());
                    episodes.saveAndFlush(episode);
                });
    }
}
