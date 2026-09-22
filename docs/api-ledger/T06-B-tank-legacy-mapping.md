# T06-B — Mapeo legacy y snapshot de nivel (S06)

Parent spec: S06. Precondition: T06-A.

## What was added

- `TankBackfillService.run()` → `TankBackfillReport(equipmentTotal, created, alreadyMapped,
  unmappable, withoutCustomer, levelMismatches)`. It maps only **classifiable** equipment
  (`TankEligibility`: positive capacity + fuel type) whose `companyId` resolves to a mapped customer;
  it is idempotent (second run creates nothing) and reports everything it left alone.
  - `CustomerDirectory.organizationIdForCustomer(...)` was added so the backfill can resolve the tank's
    tenant from the mapped customer.
- **Reading vs. metadata separated**: `TankReadingService.applyValidatedReading(tankId, level, unit,
  observedAt)` applies telemetry and ignores out-of-order (older) observations, so the snapshot never
  regresses. `applyManualLevel(...)` is the metadata path.
- **v1 adaptation**: `EquipmentCommandServiceImpl.handle(UpdateEquipmentCommand)` now mirrors the level
  of a *mapped* equipment into its Tank through `applyManualLevel` (source `MANUAL`), so the legacy
  `/api/v1/equipment/{id}/update` flow keeps working while telemetry keeps precedence. Unmapped
  equipment is untouched.
- `Equipment.hasFuelType()` added for classification without exposing the inventory enum.

No new schema in this ticket (no migration).

## Tests

`TankLegacyBackfillTest`: 3 equipment rows → 1 tank created (mappable), 1 unmappable, 1 without a mapped
customer; second run creates nothing and reports `alreadyMapped`; an older validated reading does not
regress the level while a newer one applies (`levelSource=VALIDATED`); a v1 equipment update moves the
mapped tank's level as `MANUAL`.

## Asunciones abiertas

- **A1 — legacy fuel type is not copied into Tank** during backfill (only capacity/level/name). Reading
  `FuelType.name()` from new code would add a dependency line to the frozen ArchUnit baseline, which
  T02-A forbids; the fuel type is set later by the configuration/telemetry path. Review whether we want
  to accept +1 baseline entry instead.
- **A2 — legacy equipment carries no unit**, so backfilled tanks default to `LITRE`.
- **A3 — `levelMismatches` only counts newly created tanks** (a post-hoc comparison of
  equipment vs. tank level), used as the reconciliation signal of this ticket.
