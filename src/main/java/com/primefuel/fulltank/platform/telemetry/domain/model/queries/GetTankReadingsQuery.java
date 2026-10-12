package com.primefuel.fulltank.platform.telemetry.domain.model.queries;

import java.time.Instant;

public record GetTankReadingsQuery(Long organizationId, Long tankId, Instant from, Instant to) {}
