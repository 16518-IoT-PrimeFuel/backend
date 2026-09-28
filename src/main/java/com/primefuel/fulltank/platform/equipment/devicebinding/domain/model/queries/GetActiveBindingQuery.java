package com.primefuel.fulltank.platform.equipment.devicebinding.domain.model.queries;

import java.time.Instant;

public record GetActiveBindingQuery(String deviceId, String channel, Instant instant) {
}
