package com.primefuel.fulltank.platform.equipment.application.commandservices;

public record TankBackfillReport(
        long equipmentTotal,
        long created,
        long alreadyMapped,
        long unmappable,
        long withoutCustomer,
        long levelMismatches) {
}
