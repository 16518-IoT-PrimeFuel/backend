# T19-A — Durable publication registry and event contracts (S19)

Parent spec: S19 — Publicar eventos de forma duradera.
Preconditions: T02-B (`iam.api` seam), T03-B (Flyway + `validate`).
Scope: the **publication** side only — a transactional outbox that records domain facts atomically
with the business transaction, plus the versioned envelope contract. Consumer-side idempotency,
replay, backlog and DLQ are **T19-B** and are not implemented here.

## Decision: hand-rolled transactional outbox (no Spring Modulith)

Spring Modulith is not on the classpath and was deferred in T02-A (Boot-4 line still
milestone/snapshot). Its `event_publication` machinery is therefore unavailable, so the outbox is
built explicitly.

Crucially, the existing `shared.domain.model.aggregates.AbstractDomainAggregateRoot`
(Spring Data `AbstractAggregateRoot`) **cannot** be relied on for publication: aggregates are never
persisted directly — they are mapped through hand-written assemblers to separate `*PersistenceEntity`
classes, so Spring Data would never publish their registered events. Publication is instead an
explicit call made inside the command service's transaction.

## Contracts (public surface)

- `com.primefuel.fulltank.platform.shared.events.EventEnvelope` — the common, versioned envelope
  record: `eventId` (UUID), `eventType`, `aggregateType`, `aggregateId`, `organizationId`,
  `aggregateVersion` (nullable), `occurredAt`, `payload`.
  - **Versioning:** `eventType` is a versioned identifier, convention `<aggregate>.<action>.v<n>`
    (e.g. `order.created.v1`). A semantic change requires a new `v` rather than an in-place change.
- `com.primefuel.fulltank.platform.shared.events.EventPublicationRegistry` — the SPI modules call:
  ```
  EventEnvelope publish(String eventType, String aggregateType, String aggregateId,
                        Long organizationId, Long aggregateVersion, String payloadJson);
  ```
  `payloadJson` is produced by the owning module (its own event schema); the shared kernel stays free
  of business types (S19 + the `sharedKernelDoesNotDependOnBusinessModules` ArchUnit rule).
- These live in `shared` (technical kernel) rather than a business module: the envelope/registry are
  cross-cutting technical contracts with no business rules. Module-specific event *types* will live in
  each module's `api/events` surface as they are introduced.

## Persistence

- Entity `EventPublicationPersistenceEntity` (`@Table("event_publications")`, extends the audited
  base) + `EventPublicationPersistenceRepository`.
- Migration `V3__event_publications.sql` (generated from Hibernate's MySQL DDL, like T03-A/B).
  Columns: `id`, `created_at`, `updated_at`, `event_id` (unique), `event_type`, `aggregate_type`,
  `aggregate_id`, `organization_id`, `aggregate_version` (nullable), `occurred_at`, `payload` (TEXT),
  `completed_at` (nullable).
  - `completed_at` stays `null` for now — dispatching/consuming (which would set it) is T19-B.

## Atomicity

`JpaEventPublicationRegistry` is annotated
`@Transactional(propagation = Propagation.MANDATORY)`: publishing **requires** an active transaction
and joins it, so the outbox row commits or rolls back **with** the business write. Calling it outside
a transaction fails fast instead of silently committing an orphan event. `organizationId` is required
(S19: tenant obligatorio) and rejected if null.

## Tests

`shared/events/EventPublicationRegistryTest` (H2) — 4 tests:
- publishes inside the caller's transaction and the row is persisted with the right fields;
- **rollback (simulated crash) leaves no publication** — the atomicity guarantee;
- refuses to publish outside a transaction (`IllegalTransactionStateException`);
- refuses a null `organizationId`.

Architecture baseline unchanged (**180**, no new violations). Full suite: **37/37 green**
(33 previous + 4 new).

## MySQL validation

The `V3` schema was verified against local **MySQL 8.0.46**: Flyway applied `V1`+`V2`+`V3` on an empty
scratch database and Hibernate `validate` passed — i.e. the migration matches the entity exactly.
The scratch database was dropped afterwards.

## Acceptance criteria check (Roadmap T19-A / S19)

- Registry/outbox elegido: **yes** (hand-rolled, documented rationale).
- Envelope versionado: **yes** (`EventEnvelope` + versioned `eventType` convention).
- Hecho + registro atómicos: **yes** (`Propagation.MANDATORY`, rollback test proves it).
- Test de crash: **yes** (rollback scenario; the committed row survives).
- Nueva tabla compatible: **yes** (`V3`, validated under `validate` on MySQL).
- Build verde: **yes** — 37/37.

## Notes / follow-ups (T19-B and producers)

- No business module publishes yet (no producer wired); the mechanism is in place. Introducing real
  producers (and the `api/events` per-module event types) happens as those modules gain events.
- `FuelOrderCommandServiceImpl` is **not** `@Transactional` today; if FuelOrder becomes a producer its
  write path must be transactional first (the `MANDATORY` registry will enforce this at runtime).
- `aggregateVersion` is nullable for now; it is populated once an aggregate tracks a version (e.g.
  `Delivery` in T14-A).
- T19-B adds the consumer side: `(consumer, eventId)` inbox uniqueness, replay, backlog/age metrics,
  poison handling, and the dispatcher that finally sets `completed_at`.
