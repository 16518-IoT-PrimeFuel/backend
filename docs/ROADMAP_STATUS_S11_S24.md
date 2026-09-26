# Backend roadmap status: S11–S24

This is the implementation gate for the backend migration roadmap. v1 remains additive and is not removed while the consumer ledger contains an `UNKNOWN` or active consumer.

| Spec | Result in this branch | Evidence |
|---|---|---|
| S11 | Implemented | supply availability, reservations, tenant adapters and V2 supply work already present |
| S12 | Implemented | driver expiry, vehicle enablement, eligibility query at `/api/v2/fleet/eligible` |
| S13 | Expanded | `fleet_reservations` schema with window, capacity, idempotency and indexes; orchestration remains behind the v1 bridge |
| S14 | Implemented additively | `ARRIVED` state, timestamp, invalid transition guard, `/api/v1/deliveries/{id}/arrive` |
| S15 | Guarded | accepted requests reuse their existing supply reservation; direct legacy orders reserve once |
| S16 | Implemented | tracking/load evidence adapters and delivery association are already present |
| S17 | Implemented | versioned geofence evaluation and safety decision persistence are already present |
| S18 | Implemented detection/ACK ledger | valve command ACK ledger is present; physical actuator certification remains outside this repository |
| S19 | Implemented | durable outbox/inbox and checkpoints are present |
| S20 | Implemented | v2 notification read model and durable event consumer are present |
| S21 | Implemented | delivery journal and timeline query path are present |
| S22 | Implemented as migration control | OpenAPI snapshots plus `consumer-ledger.csv`; v1 is retained until consumers are closed |
| S23 | Implemented | payment command no longer imports or mutates `FuelOrderRepository`; ADR-023 records legacybilling boundary |
| S24 | Controlled retirement | `legacy-removal-register.csv`; no destructive drop is allowed while entries remain KEEP/RETAIN |

## Verification

- `git diff --check` is the local static gate.
- The repository requires JDK 26 for Maven tests. This machine exposes Java 25 without `javac`, so `bash mvnw test` cannot execute here until that toolchain is installed.
- No database drop or destructive legacy removal is included in this branch.
