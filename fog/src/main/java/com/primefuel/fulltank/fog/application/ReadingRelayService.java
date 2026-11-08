package com.primefuel.fulltank.fog.application;

import com.primefuel.fulltank.fog.application.TelemetryForwarder.ForwardResult;
import com.primefuel.fulltank.fog.config.FogProperties;
import com.primefuel.fulltank.fog.domain.Reading;
import com.primefuel.fulltank.fog.domain.ReadingPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Recibe lecturas (HTTP o MQTT), las valida y las reenvía al backend en orden de llegada.
 *
 * <p>Si el backend no responde, la lectura vuelve a la cabeza de la cola y se reintenta con la misma
 * {@code sequence}, así el backend la deduplica si el primer intento sí llegó. Las siguientes esperan detrás para
 * no adelantar un {@code capturedAt} posterior, que haría que el backend ignore la anterior.</p>
 */
@Service
public class ReadingRelayService {

    private static final Logger log = LoggerFactory.getLogger(ReadingRelayService.class);
    private static final int RECENT_LIMIT = 50;
    private static final int SEEN_KEYS_LIMIT = 1_000;

    public enum AcceptOutcome { QUEUED, DUPLICATE }

    public record Stats(int queued, long forwarded, long quarantined, long rejected, long dropped,
                        Instant lastForwardAt, String lastError) {}

    private final ReadingPolicy policy;
    private final TelemetryForwarder forwarder;
    private final FogProperties.Queue queueConfig;
    private final Clock clock;

    private final Deque<Pending> queue = new ArrayDeque<>();
    private final Deque<RelayRecord> recent = new ArrayDeque<>();
    private final Map<String, Boolean> seenKeys = new LinkedHashMap<>(16, 0.75f, false) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
            return size() > SEEN_KEYS_LIMIT;
        }
    };
    private Pending inFlight;
    private long forwarded, quarantined, rejected, dropped;
    private Instant lastForwardAt;
    private String lastError;

    public ReadingRelayService(ReadingPolicy policy, TelemetryForwarder forwarder, FogProperties properties,
                               Clock clock) {
        this.policy = policy;
        this.forwarder = forwarder;
        this.queueConfig = properties.queue();
        this.clock = clock;
    }

    public AcceptOutcome accept(Reading reading, ReadingSource source) {
        policy.violation(reading).ifPresent(reason -> {
            log.warn("Lectura {} rechazada ({}): {}", reading.key(), source, reason);
            throw new ReadingRejectedException(reason);
        });
        synchronized (this) {
            if (seenKeys.containsKey(reading.key())) {
                log.debug("Lectura {} repetida ({}), se ignora", reading.key(), source);
                return AcceptOutcome.DUPLICATE;
            }
            seenKeys.put(reading.key(), Boolean.TRUE);
            if (queue.size() >= queueConfig.capacity()) {
                // El nivel más reciente vale más que el más antiguo: se descarta el más viejo en espera.
                Pending oldest = queue.pollFirst();
                dropped++;
                remember(oldest, RelayRecord.Status.DROPPED_QUEUE_FULL, null, "cola llena");
                log.warn("Cola llena: se descarta la lectura {}", oldest.reading.key());
            }
            Instant now = clock.instant();
            queue.addLast(new Pending(reading, source, now));
            log.info("Lectura {} encolada ({}), nivel {} {}", reading.key(), source, reading.level(), reading.unit());
            return AcceptOutcome.QUEUED;
        }
    }

    /** Reenvía lo pendiente; se detiene en la primera lectura que deba reintentarse. */
    @Scheduled(fixedDelay = 1000)
    public void drain() {
        while (true) {
            Pending next;
            synchronized (this) {
                next = queue.peekFirst();
                if (next == null || next.nextAttemptAt.isAfter(clock.instant())) {
                    return;
                }
                inFlight = queue.pollFirst();
            }
            next.attempts++;
            ForwardResult result;
            try {
                result = forwarder.forward(next.reading);
            } catch (RuntimeException e) {
                result = new ForwardResult.Retryable(e.getClass().getSimpleName() + ": " + e.getMessage());
            }
            synchronized (this) {
                inFlight = null;
                switch (result) {
                    case ForwardResult.Accepted a -> onAccepted(next, a);
                    case ForwardResult.Rejected r -> onRejected(next, r);
                    case ForwardResult.Retryable r -> {
                        onRetryable(next, r);
                        return;
                    }
                }
            }
        }
    }

    private void onAccepted(Pending p, ForwardResult.Accepted a) {
        forwarded++;
        lastForwardAt = clock.instant();
        lastError = null;
        if ("QUARANTINED".equals(a.quality())) {
            quarantined++;
            log.warn("Lectura {} quedó en CUARENTENA en el backend: revisa DEVICE_TOKEN y el vínculo del tanque",
                    p.reading.key());
        } else {
            log.info("Lectura {} reenviada: quality={} readingId={} tankId={} duplicate={}",
                    p.reading.key(), a.quality(), a.readingId(), a.tankId(), a.duplicate());
        }
        remember(p, RelayRecord.Status.FORWARDED, a, a.duplicate() ? "duplicada en backend" : null);
    }

    private void onRejected(Pending p, ForwardResult.Rejected r) {
        rejected++;
        lastError = "HTTP " + r.status() + ": " + r.detail();
        log.error("Backend rechazó la lectura {} con {}: {}", p.reading.key(), r.status(), r.detail());
        remember(p, RelayRecord.Status.REJECTED_BY_BACKEND, null, lastError);
    }

    private void onRetryable(Pending p, ForwardResult.Retryable r) {
        Duration backoff = backoffFor(p.attempts);
        p.nextAttemptAt = clock.instant().plus(backoff);
        queue.addFirst(p);
        lastError = r.detail();
        log.warn("Backend no disponible para {} (intento {}): {}. Reintento en {}s",
                p.reading.key(), p.attempts, r.detail(), backoff.toSeconds());
    }

    Duration backoffFor(int attempts) {
        Duration delay = queueConfig.initialBackoff().multipliedBy(1L << Math.min(attempts - 1, 20));
        return delay.compareTo(queueConfig.maxBackoff()) > 0 ? queueConfig.maxBackoff() : delay;
    }

    private void remember(Pending p, RelayRecord.Status status, ForwardResult.Accepted ack, String detail) {
        Reading r = p.reading;
        recent.addFirst(new RelayRecord(r.deviceId(), r.channel(), r.sequence(), r.level(), r.unit(), r.capturedAt(),
                p.source, status, ack == null ? null : ack.quality(), ack == null ? null : ack.readingId(),
                ack == null ? null : ack.tankId(), p.attempts, p.receivedAt, clock.instant(), detail));
        while (recent.size() > RECENT_LIMIT) {
            recent.pollLast();
        }
    }

    public synchronized List<RelayRecord> recent() {
        return new ArrayList<>(recent);
    }

    public synchronized Stats stats() {
        return new Stats(queue.size() + (inFlight == null ? 0 : 1), forwarded, quarantined, rejected, dropped,
                lastForwardAt, lastError);
    }

    private static final class Pending {
        final Reading reading;
        final ReadingSource source;
        final Instant receivedAt;
        int attempts;
        Instant nextAttemptAt;

        Pending(Reading reading, ReadingSource source, Instant receivedAt) {
            this.reading = reading;
            this.source = source;
            this.receivedAt = receivedAt;
            this.nextAttemptAt = receivedAt;
        }
    }
}
