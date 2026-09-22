# Database restore runbook (S03 / T03)

Purpose: make a schema-changing deployment (and its rollback) rehearsable. Introduced by T03-A
(`docs/api-ledger/T03-A-schema-baseline.md`); used by T03-B when the runtime switches from implicit
DDL to Flyway `validate`.

Environment variables referenced (same names the app already uses):
`DATABASE_URL`, `DATABASE_PORT`, `DATABASE_NAME`, `DATABASE_USER`, `DATABASE_PASSWORD`.

## 1. Pre-flight (before any schema-affecting deploy)

1. Confirm the target `DATABASE_NAME` and that no other migration is running.
2. Take a full logical backup:
   ```
   mysqldump --single-transaction --routines --triggers \
     -h "$DATABASE_URL" -P "$DATABASE_PORT" -u "$DATABASE_USER" -p \
     "$DATABASE_NAME" > "backup_${DATABASE_NAME}_$(date +%Y%m%d_%H%M%S).sql"
   ```
3. Record the current schema fingerprint for post-deploy comparison:
   ```
   mysqldump --no-data --skip-comments -h "$DATABASE_URL" -P "$DATABASE_PORT" \
     -u "$DATABASE_USER" -p "$DATABASE_NAME" > schema_before.sql
   ```
4. If deploying a migration, confirm `V1__baseline.sql` (and any later `V*`) is the intended set.

## 2. Restore rehearsal (run before trusting a rollback plan)

On a **throwaway** database (never production):

1. Create an empty database and restore the backup:
   ```
   mysql -h "$DATABASE_URL" -P "$DATABASE_PORT" -u "$DATABASE_USER" -p \
     -e "CREATE DATABASE ${DATABASE_NAME}_restore_test"
   mysql -h "$DATABASE_URL" -P "$DATABASE_PORT" -u "$DATABASE_USER" -p \
     "${DATABASE_NAME}_restore_test" < "backup_....sql"
   ```
2. Verify table count and a few row counts match the source.
3. Start the application against `${DATABASE_NAME}_restore_test` and confirm it boots.
4. Drop the throwaway database.

## 3. Flyway adoption for existing databases (T03-B)

T03-B already applied this: `spring.flyway.enabled=true` + `baseline-on-migrate=true` +
`baseline-version=1`, `spring.jpa.hibernate.ddl-auto=validate`, and the two `ALTER`s moved into
`V2__normalize_legacy_enum_columns.sql`. Against a database that already has the schema:

1. Flyway records the existing schema as already at `V1` (`baseline-on-migrate`) and applies `V2`.
2. `validate` then checks the migrated schema against the entity mappings.
3. Compare `schema_before.sql` with a fresh `mysqldump --no-data` after migration; investigate any
   drift before declaring success (drift goes to quarantine, it is never forced).

## 4. Rollback

- Schema changes are expand-only: the **previous binary must remain compatible with the expanded
  schema**. Preferred rollback = deploy the previous binary, keep the new tables/columns.
- `DROP`/contract changes are a separate, later, authorized release — not part of a rollback.
- If a destructive change slipped through, restore from the backup in step 1, then re-run step 3.

## 5. Open gap

This runbook cannot be fully rehearsed in CI yet: the project has no MySQL test harness (only H2 in
memory). Wiring MySQL Testcontainers is the recommended follow-up recorded in
`docs/api-ledger/T03-A-schema-baseline.md`.
