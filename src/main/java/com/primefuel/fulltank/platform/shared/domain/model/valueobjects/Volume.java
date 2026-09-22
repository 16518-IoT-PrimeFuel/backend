package com.primefuel.fulltank.platform.shared.domain.model.valueobjects;

/**
 * A non-negative, finite quantity of fuel expressed in a known {@link Unit}. Legacy storage is a bare
 * {@code Double}; the migration normalises it here without reinterpreting the legacy magnitude
 * (assumption A1 of T11-A: the legacy value is already in the product's declared unit, never negative).
 */
public record Volume(double amount, Unit unit) {

    public Volume {
        if (!Double.isFinite(amount)) {
            throw new IllegalArgumentException("Volume amount must be finite");
        }
        if (amount < 0) {
            throw new IllegalArgumentException("Volume amount must not be negative");
        }
        if (unit == null) {
            throw new IllegalArgumentException("Volume unit is required");
        }
    }

    public static Volume of(double amount, Unit unit) {
        return new Volume(amount, unit);
    }

    public static Volume litres(double amount) {
        return new Volume(amount, Unit.LITRE);
    }

    public static Volume gallons(double amount) {
        return new Volume(amount, Unit.GALLON);
    }

    public Volume convertedTo(Unit target) {
        return new Volume(target.fromLitres(unit.toLitres(amount)), target);
    }

    public Volume plus(Volume other) {
        return new Volume(amount + other.convertedTo(unit).amount, unit);
    }

    public boolean atLeast(Volume other) {
        return amount >= other.convertedTo(unit).amount;
    }

    public boolean exceeds(Volume other) {
        return amount > other.convertedTo(unit).amount;
    }
}
