package com.primefuel.fulltank.platform.fleet.domain.model.valueobjects;

/**
 * Typed driver state (S12: "estados tipados"). The legacy column is a free-text {@code varchar}; the
 * values here are the ones the current flow actually produces ({@code AVAILABLE}, {@code ASSIGNED}) plus
 * the eligibility-relevant {@code SUSPENDED}/{@code INACTIVE}. Unknown codes are rejected at the boundary
 * so a typo cannot silently become an uneligible-but-"active" state.
 */
public enum DriverStatus {

    AVAILABLE,
    ASSIGNED,
    SUSPENDED,
    INACTIVE;

    public static DriverStatus fromCode(String code) {
        if (code == null || code.isBlank()) {
            return AVAILABLE;
        }
        try {
            return valueOf(code.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown driver status: " + code);
        }
    }
}
