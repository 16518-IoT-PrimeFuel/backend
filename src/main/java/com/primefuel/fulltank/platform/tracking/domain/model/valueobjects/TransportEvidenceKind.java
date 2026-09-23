package com.primefuel.fulltank.platform.tracking.domain.model.valueobjects;

import java.util.Locale;

/** The two payload variants the transport-evidence endpoint accepts (S16/T16-A). */
public enum TransportEvidenceKind {
    POSITION,
    LOAD;

    public static TransportEvidenceKind fromCode(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("An evidence type is required");
        }
        try {
            return valueOf(code.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown evidence type: " + code);
        }
    }
}
