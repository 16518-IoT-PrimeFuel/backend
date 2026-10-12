package com.primefuel.fulltank.platform.shared.interfaces.rest;

/** Raised when a caller exceeds a rate limit; mapped to 429 with a Retry-After header. */
public class RateLimitExceededException extends RuntimeException {
    private final long retryAfterSeconds;

    public RateLimitExceededException(long retryAfterSeconds) {
        super("Too many requests");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
