# T08-B — Integración idempotente con Tank (S08)

Parent spec: S08. Precondition: T08-A.

## What was added

- `ValidatedTankReadingConsumer` (telemetry) consumes `ValidatedTankReadingEvent` and applies it to the
  tank through `equipment.api.TankAssets.applyValidatedReading(...)`.
- **Dedup and application share one transaction**: `EventInbox.consume(consumer, eventId)` marks the
  reading as consumed, and the tank update happens in the same transaction. The inbox row therefore only
  commits when the tank was updated, so:
  - a **replay** is consumed once and changes nothing;
  - a **crash** before commit leaves the reading unconsumed, so a retry re-applies it (nothing silently
    lost);
  - a reading that **cannot be applied** (e.g. above capacity) rolls the consumption back and remains
    retryable.
- The event id is `deviceId:channel:sequence`, which is the same identity the ingest dedup uses.
- `equipment.api.TankAssets` gained `applyValidatedReading(tankId, level, unit, observedAt)` returning
  whether the snapshot moved: **out-of-order readings are ignored** so the latest level never regresses.
- Raw data is preserved: the reading row (and its quarantine reason, if any) stays in
  `telemetry_readings` regardless of what the tank integration does.
- No schema change in this ticket.

## Tests

`TankReadingIntegrationTest`:
- a reading applies once (`levelSource=VALIDATED`);
- the **same event replayed** changes nothing (consumed once);
- an **older** reading is accepted by the inbox but ignored by the tank — the level and its instant stay
  the newer ones;
- a newer reading still advances the snapshot;
- a reading **above capacity throws and is not marked as consumed**, so the retry path applies the
  corrected reading afterwards.

## Asunciones abiertas

- **A1 — consumer is in-process** (`@EventListener`) and single-instance. Multi-instance safety relies on
  the `(consumer, event_id)` unique constraint, which already makes double consumption impossible; the
  durable dispatcher/backlog work stays deferred with T19-B.
- **A2 — poison messages retry indefinitely** (documented as out of scope in T19-B); the failed reading
  stays visible in `telemetry_readings` and in the logs for operations.
- **A3 — ordering uses `capturedAt` only.** No device clock-skew correction was introduced; S08 excludes
  it, and a skewed device simply stops advancing the snapshot until a newer reading arrives.
