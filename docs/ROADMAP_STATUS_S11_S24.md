# Backend roadmap status: S11–S24

This is the implementation gate for the backend migration roadmap. v1 remains additive and is not removed while the consumer ledger contains an `UNKNOWN` or active consumer.

| Spec | Result in this branch | Evidence |
|---|---|---|
| S11 | Implemented | active tenant-safe catalog at `/api/v2/products`; supply availability and idempotent reservations |
| S12 | Implemented | driver expiry, vehicle enablement, eligibility query at `/api/v2/fleet/eligible` |
| S13 | Implemented | locked driver/vehicle rows, overlap check, capacity/window validation, idempotency and release |
| S14 | Implemented additively | `ARRIVED` state, timestamp, invalid transition guard, `/api/v1/deliveries/{id}/arrive` |
| S15 | Implemented | delivery service uses ports, reserves supply/fleet before assignment, and rolls back failed dispatch |
| S16 | Implemented | append-only tracking plus ordered/latest queries and tenant-safe delivery association |
| S17 | Implemented detection mode | versioned geofence evaluation and safety decision persistence are active; prevention remains disabled |
| S18 | Implemented detection/ACK ledger | valve command ACK ledger is present; physical actuator certification remains outside this repository |
| S19 | Implemented | durable outbox, scheduled relay, retry-safe event envelope and checkpoints are present |
| S20 | Implemented | v2 notification read model, event fanout and source-event idempotency are present |
| S21 | Implemented | append-only lifecycle/tracking journal and tenant-safe timeline query are present |
| S22 | Implemented as migration control | Local Mobile-app audit recorded in `consumer-ledger.csv`; its API providers now default to backend v1, but executable v2 consumers are still absent, so v1 remains |
| S23 | Implemented | payment command no longer imports or mutates `FuelOrderRepository`; ADR-023 records legacybilling boundary |
| S24 | Controlled retirement | Local consumer evidence is documented; no destructive drop is allowed while v1 clients, mocks, or KEEP/RETAIN entries remain |

## Verification

- `git diff --check` is the local static gate.
- The repository targets JDK 26; the suite was executed with the installed JDK 25 using `-Dmaven.compiler.release=25` without modifying `pom.xml`.
- No database drop or destructive legacy removal is included in this branch.
