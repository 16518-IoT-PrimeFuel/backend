/**
 * Versioned event contract of the {@code tracking} module (S16/T16-A).
 *
 * <p>Currently published: {@link com.primefuel.fulltank.platform.tracking.api.events.DeliveryTelemetryReceived}
 * (a driver-app position sample).
 *
 * <p><strong>Reserved for T18 (not implemented here).</strong> The original S16 contract also names
 * {@code ValveStateObserved}. T18-A/T18-B will emit it from the {@code tracking} module (the driver app is
 * the actor, no IoT device), and its shape is reserved as:
 *
 * <pre>{@code
 * record ValveStateObserved(
 *         Long deliveryId,
 *         Long providerId,
 *         Long driverId,
 *         String valveState,     // e.g. OPEN / CLOSED
 *         String commandId,      // correlates with the T18 valve command, nullable when spontaneous
 *         Instant observedAt,
 *         Instant occurredAt) {
 * }
 * }</pre>
 *
 * <p>Nothing here blocks that name or shape: T18 reuses this package and the same outbox/via
 * {@code EventPublicationRegistry} pattern. Implementing it now would be out of scope for T16-A.
 */
package com.primefuel.fulltank.platform.tracking.api.events;
