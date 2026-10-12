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

    /** Strict parse for API inputs: blank/unknown codes are empty instead of degrading to LITRE. */
    public static java.util.Optional<Unit> parse(String code) {
        if (code == null || code.isBlank()) {
            return java.util.Optional.empty();
        }
        return switch (code.trim().toUpperCase()) {
            case "GAL", "GALLON", "GALON", "GALONES", "GALLONS" -> java.util.Optional.of(GALLON);
            case "L", "LT", "LITRE", "LITRES", "LITER", "LITERS", "LITRO", "LITROS" -> java.util.Optional.of(LITRE);
            default -> java.util.Optional.empty();
        };
    }

    /** Lenient mapping for legacy reads only; unknown or blank codes default to LITRE. */
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
