package com.primefuel.fulltank.platform.telemetry.domain.model.queries;

import java.time.Instant;

public record GetProviderTankReadingsQuery(
        Long providerId, Long tankId, Instant from, Instant to) {}
