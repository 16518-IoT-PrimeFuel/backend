package com.primefuel.fulltank.platform.iam.interfaces.rest;

import com.primefuel.fulltank.platform.shared.interfaces.rest.RateLimitExceededException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sliding-window rate limiter for the public /api/authentication endpoints.
 *
 * <p>Security note: state lives in this JVM's memory, so limits are per instance. With several
 * instances (e.g. more than one Render instance) the effective limit is multiplied and the state is
 * lost on restart; move the counters to a shared store (Redis/DB) in that case. The client IP is
 * {@code getRemoteAddr()} only: behind a proxy, configure {@code server.forward-headers-strategy=native}
 * so the container resolves it from X-Forwarded-For sent by trusted proxies. The header is never read
 * here, so a client cannot dodge the limit by spoofing it; the per-username bucket bounds brute
 * force on one account regardless of source IP.</p>
 */
@Component
public class AuthRateLimiter {
    private static final int CLEANUP_THRESHOLD = 10_000;

    private final Map<String, ArrayDeque<Long>> hits = new ConcurrentHashMap<>();
    private final boolean enabled;
    private final long windowMillis;
    private final int signIn;
    private final int signUp;
    private final int resetRequest;
    private final int resetConfirm;

    public AuthRateLimiter(
            @Value("${app.rate-limit.enabled:true}") boolean enabled,
            @Value("${app.rate-limit.window-seconds:60}") long windowSeconds,
            @Value("${app.rate-limit.sign-in:10}") int signIn,
            @Value("${app.rate-limit.sign-up:10}") int signUp,
            @Value("${app.rate-limit.password-reset-request:5}") int resetRequest,
            @Value("${app.rate-limit.password-reset-confirm:10}") int resetConfirm) {
        this.enabled = enabled;
        this.windowMillis = windowSeconds * 1000;
        this.signIn = signIn;
        this.signUp = signUp;
        this.resetRequest = resetRequest;
        this.resetConfirm = resetConfirm;
    }

    public void signIn(HttpServletRequest request, String username) {
        String ip = clientIp(request);
        hit("sign-in:" + ip + "|" + normalize(username), signIn);
        hit("sign-in-ip:" + ip, signIn * 5); // many usernames from one address
        hit("sign-in-user:" + normalize(username), signIn * 3); // one account from many addresses
    }

    public void signUp(HttpServletRequest request) {
        hit("sign-up:" + clientIp(request), signUp);
    }

    public void passwordResetRequest(HttpServletRequest request, String email) {
        hit("reset-request:" + clientIp(request) + "|" + normalize(email), resetRequest);
    }

    public void passwordResetConfirm(HttpServletRequest request) {
        hit("reset-confirm:" + clientIp(request), resetConfirm);
    }

    private void hit(String key, int limit) {
        if (!enabled) return;
        long now = System.currentTimeMillis();
        if (hits.size() > CLEANUP_THRESHOLD) {
            hits.values().removeIf(q -> {
                synchronized (q) {
                    return q.isEmpty() || now - q.peekLast() >= windowMillis;
                }
            });
        }
        var window = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (window) {
            while (!window.isEmpty() && now - window.peekFirst() >= windowMillis) window.pollFirst();
            if (window.size() >= limit) {
                long retryAfterMillis = windowMillis - (now - window.peekFirst());
                throw new RateLimitExceededException(Math.max(1, (retryAfterMillis + 999) / 1000));
            }
            window.addLast(now);
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String clientIp(HttpServletRequest request) {
        return request.getRemoteAddr();
    }
}
