package com.primefuel.fulltank.platform.replenishment.domain.model.commands;

public record RejectReplenishmentRequestCommand(Long requestId, String reason) {
}
