# T08-A — Adapter y almacenamiento normalizado de telemetría (S08)

Parent spec: S08. Preconditions: T07-B, T19-B. U10 (protocolo no definitivo) is out of scope, so the
simplest standard was chosen and recorded as a technical decision.

## What was added (new module `telemetry`)

- **Versioned payload** `POST /api/v2/telemetry/readings` (`schemaVersion` is mandatory; only version `1`
  is supported, anything else is a validation error). The device token travels in the `X-Device-Token`
  header, never in the body.
- `TelemetryReader`... more precisely, `TelemetryIngestService`:
  1. version check, required sequence/capturedAt;
  2. **normalisation** through the shared `Volume`/`Unit` (finite, non-negative, unit code mapped);
  3. **dedup by `(device, channel, sequence)`** — a replay is acknowledged and writes nothing (the DB
     unique constraint is the backstop for concurrent replays);
  4. **machine authentication** through `equipment.api.DeviceAuthentication`, resolving the tank *at the
     reading's instant*;
  5. storage of `capturedAt` and `receivedAt` **separately**, and publication of
     `ValidatedTankReadingEvent` only for accepted readings.
- **Observation only**: no threshold, no episode, no order — that is S09's job.
- Quarantined readings are **stored, not dropped** (with the reason: `UNKNOWN_CREDENTIAL`,
  `REVOKED_CREDENTIAL`, `NO_ACTIVE_BINDING`), which is the measurable observation mode S08 asks for.
- Persistence `telemetry_readings` (`V14__telemetry_readings.sql`, unique `(device_id, channel,
  sequence_number)`, append-only) validated on MySQL 8.0.46.
- Security: `/api/v2/telemetry/**` is exempt from the user JWT filter chain because a device
  authenticates with its rotating token (T07-B) — a commented, deliberate matcher in
  `WebSecurityConfiguration`.

## Tests

`TelemetryIngestTest`: accepted reading stores and publishes once; a replay (even with a different level)
is a no-op and keeps the original value and a single publication; forged token and out-of-period reading
are quarantined with their reason and counted; unsupported schema version, `NaN` and negative levels are
rejected without writing.

## Asunciones abiertas

- **A1 (technical decision, not business) — protocol.** S08/T08-A explicitly excludes "protocolo
  definitivo": HTTPS + rotating bearer token, JSON payload versioned by `schemaVersion`. No MQTT, no
  batch endpoint, no compression.
- **A2 — publication is in-process** (`ApplicationEventPublisher`). The durable outbox dispatcher was
  already deferred in T19-B; when it lands, this publisher is the single place to swap.
- **A3 — rate limiting / payload size caps are not implemented yet** (S08 lists them as operable limits);
  they belong with the deployment/RUNBOOK work, not with the domain ticket.
