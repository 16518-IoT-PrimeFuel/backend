# T03-A — Schema inventory and migration baseline (S03)

Parent spec: S03 — Versionar la evolución del esquema.
Precondition: T01-B (`docs/api-ledger/T01-B-state-security-characterization.md`).
Scope: capture the current schema and introduce the migrator. **No runtime behaviour changes in this
ticket** — Flyway is wired but disabled, and the two startup mutators (`ddl-auto=update`,
`MySqlSchemaCompatibilityInitializer`) are left exactly as they are. Removing the implicit DDL and
switching the runtime to `validate` is T03-B.

## Decision: migration tool

**Chosen: Flyway** (SQL-native, version-numbered migrations under `db/migration`).

| Option | Verdict |
|---|---|
| **Flyway** | **Chosen.** Versioned plain-SQL files, no extra abstraction layer. Version is managed by the Spring Boot 4.0.6 BOM (no pinned coordinate in `pom.xml`). `flyway-mysql` is added for MySQL support (Flyway 10+ ships DB support as separate modules). Matches the roadmap's "expand-only, versionado para DDL" requirement with the least machinery. |
| Liquibase | Rejected for now: XML/YAML changelog indirection is more than this schema needs (17 tables, no procedures), and the tooling weight is not justified by the migration catalogue (DB-01..DB-19). |

## Schema inventory

15 `@Entity` classes (1 `@MappedSuperclass`: `AuditableAbstractPersistenceEntity`) resolve to **17
physical tables** via the `SnakeCaseWithPluralizedTablePhysicalNamingStrategy`:

| # | Table | Owning module | Notes |
|---|---|---|---|
| 1 | `users` | iam | + `user_roles` join table |
| 2 | `roles` | iam | no audit columns |
| 3 | `buyer_companies` | iam | unique `ruc` |
| 4 | `provider_companies` | iam | unique `ruc`; + `provider_company_fuel_types` collection table |
| 5 | `password_reset_tokens` | iam | no audit columns |
| 6 | `fuel_products` | inventory | |
| 7 | `equipment` | equipment | |
| 8 | `fuel_requests` | ordering | `status` enum(...) |
| 9 | `fuel_orders` | ordering | `status` VARCHAR(30) (`@JdbcTypeCode`); unique `request_id` |
| 10 | `notifications` | notification | `type` VARCHAR(40) (`@JdbcTypeCode`) |
| 11 | `payments` | payment | `status`/`payment_method` enum(...) |
| 12 | `deliveries` | fulfillment | `status` enum(...) |
| 13 | `drivers` | fulfillment | unique `license_number` |
| 14 | `vehicles` | fulfillment | unique `license_plate` |
| 15 | `provider_ratings` | catalog | unique (`company_id`,`provider_id`) |
| 16 | `user_roles` | iam | implicit `@JoinTable` (users↔roles, EAGER) |
| 17 | `provider_company_fuel_types` | iam | implicit `@CollectionTable` |

There are no `@ManyToOne`/`@OneToMany`/`@OneToOne` associations: all cross-table links are plain
`*_id` columns (logical FKs, no DB-level FK constraints from JPA). The only JPA-managed relations are
`user_roles` and `provider_company_fuel_types`.

## Baseline

`src/main/resources/db/migration/V1__baseline.sql` — the full current schema, expand-only.

How it was produced (no live MySQL required): Hibernate's JPA schema-generation **script** action was
run against the current entity mappings with `hibernate.dialect=org.hibernate.dialect.MySQLDialect`
and an H2 datasource used only as a bootstrap — i.e. `ddl-auto=none` + `jakarta.persistence.
schema-generation.scripts.action=create` + `...scripts.create-target=...`. That emits the exact MySQL
DDL Hibernate would create for a fresh database (`engine=InnoDB`, `enum(...)` columns, generated
constraint names), which was then de-duplicated into the migration. The temporary generator test was
removed; the SQL is now a checked-in artifact.

Key facts captured in the baseline:

- `fuel_orders.status` is `VARCHAR(30)` and `notifications.type` is `VARCHAR(40)` in a fresh schema —
  i.e. the two `MySqlSchemaCompatibilityInitializer` `ALTER`s are **no-ops on a fresh database**; they
  only matter for pre-existing legacy databases where those columns were `enum(...)`.
- The other status-like columns (`fuel_requests.status`, `deliveries.status`, `payments.status`,
  `payments.payment_method`, `equipment.equipment_type`, `roles.name`) remain `enum(...)` — recorded
  as-is, not "corrected" (characterize ≠ fix).

## Environment comparison / drift sources (today)

| Source | Effect |
|---|---|
| `spring.jpa.hibernate.ddl-auto=update` (`dev` + `mysql` profiles) | mutates structure on every startup, unversioned |
| `createDatabaseIfNotExist=true` (JDBC URL) | auto-creates the database/schema |
| `MySqlSchemaCompatibilityInitializer` (`@EventListener(ApplicationReadyEvent)`) | 2 raw `ALTER TABLE ... MODIFY COLUMN` on MySQL only (no-op on H2) |
| **`V1__baseline.sql` (this ticket)** | authoritative, versioned declared schema |

Until a real MySQL `INFORMATION_SCHEMA` snapshot is captured (see limitation below), the generated
baseline is the authoritative record of the declared schema; the runtime legacy path is documented
above rather than forced to match.

## Configuration

- `pom.xml`: `org.flywaydb:flyway-core` + `org.flywaydb:flyway-mysql` + `org.springframework.boot:spring-boot-flyway`
  (all Boot-managed). The `spring-boot-flyway` module is required: in Boot 4 the Flyway
  auto-configuration lives in its own artifact, so `flyway-core` alone is not wired (verified — without
  it `ddl-auto=validate` failed with "missing table [buyer_companies]" because no migration ran).
- `application.properties`: `spring.flyway.enabled=false`, `spring.flyway.locations=classpath:db/migration`.

Disabled by default so startup is byte-for-byte unchanged until T03-B. T03-B will enable it, adopt
`baseline-on-migrate` for existing databases, and set `ddl-auto=validate`.

## Restore runbook

See `docs/runbooks/database-restore.md` (backup/restore rehearsal + Flyway baseline/validate steps).

## MySQL validation (local instance)

The baseline was validated against a real **local MySQL 8.0.46**. The project has no Testcontainers
harness yet, so this was done once, manually, with temporary throwaway tests (removed afterwards):

- `V1__baseline.sql` applied cleanly on an empty scratch database (`fulltank_baseline_check`):
  *Flyway: "Successfully applied 1 migration ... now at version v1"*.
- Hibernate then booted with `ddl-auto=validate` against that migrated schema and **passed** — i.e. the
  generated baseline is exactly equivalent to the current entity mappings (no missing or mismatched
  table/column).
- `INFORMATION_SCHEMA` after migration: **18 tables** = the 17 baseline tables + `flyway_schema_history`.
- The scratch database was dropped afterwards; nothing was left on the instance.

This satisfies T03-A's "baseline represents each authorized schema" and the MySQL metadata check for
the **empty-database** path.

## Remaining gaps

- **Legacy upgrade path** (an existing pre-baseline MySQL database + `baseline-on-migrate`) — **closed
  by T03-B** (`docs/api-ledger/T03-B-startup-ddl-replacement.md`), validated on MySQL 8.0.46. A
  **restore rehearsal** is still only documented (`docs/runbooks/database-restore.md`), not executed.
- Drift detection is not yet automatable in CI. Recommended follow-up: wire a MySQL Testcontainers
  profile so T03-B's empty-vs-legacy convergence and restore rehearsal can run unattended.

## Acceptance criteria check (Roadmap T03-A / S03)

- Migrador elegido y documentado: **yes** (Flyway, Boot-managed).
- Baseline capturado y versionado: **yes** (`V1__baseline.sql`, 29 statements, 17 tables).
- Inventario de esquema: **yes** (table above, cross-checked against the entity inventory).
- Restore runbook: **yes** (`docs/runbooks/database-restore.md`).
- Comparación de entornos / drift: **parcial** — drift sources documented; the empty-database path was
  verified on MySQL 8.0.46 (`INFORMATION_SCHEMA`, 18 tables) and Hibernate `validate` passes; the
  legacy-upgrade path is deferred to T03-B.
- Build sigue verde: **yes** — `./mvnw test`, 33/33 (Flyway disabled by default, no test impact).
