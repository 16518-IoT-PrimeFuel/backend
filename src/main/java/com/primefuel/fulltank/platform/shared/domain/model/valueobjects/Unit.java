package com.primefuel.fulltank.platform.shared.domain.model.valueobjects;

public enum Unit {

    LITRE(1.0),
    GALLON(3.785411784);

    private final double factorToLitre;

    Unit(double factorToLitre) {
        this.factorToLitre = factorToLitre;
    }

    public double toLitres(double amount) {
        return amount * factorToLitre;
    }

    public double fromLitres(double litres) {
        return litres / factorToLitre;
    }

    /**
     * Maps a legacy free-text unit code onto a known unit. Unknown or blank codes default to LITRE
     * (the conservative assumption documented in T11-A, since the legacy column is a bare String).
     */
    public static Unit fromCode(String code) {
        if (code == null || code.isBlank()) {
            return LITRE;
        }
        return switch (code.trim().toUpperCase()) {
            case "GAL", "GALLON", "GALON", "GALONES", "GALLONS" -> GALLON;
            default -> LITRE;
        };
    }
}
