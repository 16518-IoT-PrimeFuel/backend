package com.primefuel.fulltank.platform.equipment.api.events;

import java.time.Instant;

/**
 * Public contract of the equipment module: published when a tank level is edited manually (the legacy v1
 * path through {@code EquipmentCommandServiceImpl}). It exists so business policies that care about the
 * tank level — the refill policy (S09) — react to manual edits with the same guarantees as to telemetry,
 * without depending on the frozen IoT telemetry/devicebinding infrastructure.
 *
 * @param sourceKey a deterministic identity for this manual edit ({@code manual:<epochMilli>}), used for
 *                  dedup and to seed the idempotent episode key.
 */
public record TankLevelManuallyUpdatedEvent(
        Long tankId,
        Long organizationId,
        Instant observedAt,
        String sourceKey) {
}
