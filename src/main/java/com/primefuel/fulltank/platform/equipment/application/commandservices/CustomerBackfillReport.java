package com.primefuel.fulltank.platform.equipment.application.commandservices;

public record CustomerBackfillReport(
        long legacyTotal,
        long newlyMapped,
        long alreadyMapped,
        long newlyQuarantined,
        long alreadyQuarantined) {
}
