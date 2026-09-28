package com.primefuel.fulltank.platform.fleet.domain.model.valueobjects;

/**
 * Typed tanker state (S12). {@code IN_ROUTE} is included because the legacy delivery flow assigns it when
 * a tanker is used, and {@code MAINTENANCE}/{@code SUSPENDED}/{@code INACTIVE} are the non-eligible states.
 */
public enum TankerStatus {

    AVAILABLE,
    IN_ROUTE,
    MAINTENANCE,
    SUSPENDED,
    INACTIVE;

    public static TankerStatus fromCode(String code) {
        if (code == null || code.isBlank()) {
            return AVAILABLE;
        }
        try {
            return valueOf(code.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown tanker status: " + code);
        }
    }
}
