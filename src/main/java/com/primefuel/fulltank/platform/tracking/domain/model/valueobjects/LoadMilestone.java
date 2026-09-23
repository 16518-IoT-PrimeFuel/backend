package com.primefuel.fulltank.platform.tracking.domain.model.valueobjects;

import java.util.Locale;

/** A discrete load milestone reported by the driver app (S16): the truck was loaded or unloaded. */
public enum LoadMilestone {
    LOADED,
    UNLOADED;

    public static LoadMilestone fromCode(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("A load milestone is required");
        }
        try {
            return valueOf(code.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown load milestone: " + code);
        }
    }
}
