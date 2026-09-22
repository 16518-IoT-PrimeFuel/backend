# T03-B — Controlled replacement of startup DDL (S03)

Parent spec: S03 — Versionar la evolución del esquema.
Precondition: T03-A (`docs/api-ledger/T03-A-schema-baseline.md`).
Scope: make the **migrator** the only thing that changes the schema at runtime. The implicit,
unversioned DDL (`ddl-auto=update` + the code-based `MySqlSchemaCompatibilityInitializer`) is replaced
by versioned migrations and `ddl-auto=validate`.

## What changed

| Before | After |
|---|---|
| `spring.jpa.hibernate.ddl-auto=update` (dev, mysql) | `validate` |
| `MySqlSchemaCompatibilityInitializer` ran 2 raw `ALTER TABLE` on `ApplicationReadyEvent` | removed — the two `ALTER`s are now versioned in `V2` |
| Flyway disabled everywhere | enabled in `dev` + `mysql` profiles, disabled in the base (so tests stay on H2 `create-drop`) |

Files:

- `src/main/resources/db/migration/V2__normalize_legacy_enum_columns.sql` — owns the legacy
  normalization that used to live in Java:
  `alter table fuel_orders modify column status VARCHAR(30) not null;` and
  `alter table notifications modify column type VARCHAR(40) not null;`
- `application-dev.properties` / `application-mysql.properties` — `ddl-auto=validate` +
  `spring.flyway.enabled=true`, `baseline-on-migrate=true`, `baseline-version=1`.
- Removed `shared/infrastructure/persistence/jpa/configuration/MySqlSchemaCompatibilityInitializer.java`
  (and its line in `docs/diagrams/shared.puml`). It was referenced only by docs.

`V2` is a no-op on a database created by `V1` (those columns are already `VARCHAR`); on a **legacy**
database where they were `enum(...)`, it performs the conversion. Migrating the `ALTER` into a
versioned file and enabling `baseline-on-migrate` is what lets both a fresh and an existing database
converge under `validate`.

## Empty + legacy convergence (validated on local MySQL 8.0.46)

Both paths were exercised against a real MySQL instance with temporary throwaway tests (removed
afterwards; scratch DB `fulltank_baseline_check` dropped):

**Empty database** — Flyway ran `V1` then `V2`, then Hibernate `validate` passed:
```
Migrating schema `fulltank_baseline_check` to version "1 - baseline"
Migrating schema `fulltank_baseline_check` to version "2 - normalize legacy enum columns"
Successfully applied 2 migrations to schema `fulltank_baseline_check`, now at version v2
```

**Legacy database** — simulated by downgrading `fuel_orders.status` + `notifications.type` back to
`enum(...)` and dropping `flyway_schema_history`, i.e. a non-empty schema with no history. Flyway
baselined at `v1`, then applied `V2` and `validate` passed:
```
Creating Schema History table `fulltank_baseline_check`.`flyway_schema_history` with baseline ...
Successfully baselined schema with version: 1
Migrating schema `fulltank_baseline_check` to version "2 - normalize legacy enum columns"
Successfully applied 1 migration to schema `fulltank_baseline_check`, now at version v2
```

This satisfies S03's exit criteria "BD vacía y snapshot legado convergen" and "arranque no ejecuta
DDL fuera del migrador" (the Java initializer is gone; only Flyway writes DDL).

## Tests

- The normal suite keeps `spring.profiles.active=test` + H2 + `ddl-auto=create-drop`, and the base
  `spring.flyway.enabled=false` keeps Flyway off there (its MySQL DDL could not run on H2). Full suite:
  **33/33 green**.
- The empty/legacy convergence was proven separately against MySQL, as above.

## Rollback

- Migrations are expand-only and forward-only; the previous binary remains compatible with the
  migrated schema. To roll back the *behaviour*, set `ddl-auto` back and re-enable Flyway only if the
  schema is compatible; the Java initializer would have to be restored only if the DB still had the
  legacy `enum` columns.
- `docs/runbooks/database-restore.md` covers backup/restore and the adoption steps.

## Acceptance criteria check (Roadmap T03-B / S03)

- Empty + upgrade convergen: **yes** (both proven on MySQL 8.0.46).
- Runtime `validate`: **yes** (`ddl-auto=validate` in dev/mysql).
- DDL implícito eliminado / listener retirado tras equivalencia: **yes** (initializer removed; `V2`
  owns the ALTERs).
- Migrations expanded (no `DROP`): **yes** (`V2` only modifies column types).
- Build sigue verde: **yes** — 33/33.

## Notes / follow-ups

- Legacy Removal Register item **L06** (startup DDL) is now resolved.
- No MySQL Testcontainers harness exists, so the convergence checks above are manual/one-off; a
  committed MySQL test profile is still the recommended follow-up (recorded in T03-A).
- Tests deliberately do not run Flyway (H2 vs MySQL DDL). If a shared H2/MySQL migration path is
  wanted later, the baseline would need portable DDL or per-dialect migrations.
