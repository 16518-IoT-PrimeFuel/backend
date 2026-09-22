# T06-A — Modelo Tank e interfaz de activos (S06)

Parent spec: S06. Preconditions: T05-B, T11-B.

## What was added (module `equipment`)

- Aggregate `Tank` (`organizationId`, `customerAccountId`, `siteId`, `name`, `fuelType`,
  `classification`, `legacyEquipmentId`, `configurationVersion`, `capacity`, `currentLevel`,
  `levelObservedAt`, `levelSource`, `active`) and `TankConfiguration` (versioned snapshot of
  capacity/unit/fuel type).
- Volume/units were promoted to the technical kernel (`shared.domain.model.valueobjects.Volume` /
  `Unit`) so both `supply` and `equipment` share one definition; the duplicate under `supply` was
  removed and its callers/tests point at the shared type.
- Commands `RegisterTankCommand` (unit + initial level) and `UpdateTankConfigurationCommand`;
  queries `GetTankByIdQuery` / `GetTanksByOrganizationQuery`; `TankCommandService` /
  `TankQueryService`.
- Invariants enforced in the aggregate: capacity must be positive, `0 ≤ level ≤ capacity`, a site (if
  given) must belong to the same organization **and** customer, and a legacy equipment id can map to
  only one tank.
- Configuration versioning: registering creates version 1; changing capacity/fuel type bumps the
  version and appends a `TankConfiguration` row with the new capacity.
- `TankEligibility.isMappable(tankCapacity, hasFuelType)` — only classifiable equipment (positive
  capacity + a fuel type) will ever create a Tank (no mass rename).
- Seam `equipment.api.TankAssets` (`findById`, `tankIdForLegacyEquipment`).
- Persistence: `tanks` (unique `legacy_equipment_id`) + `tank_configurations`
  (`V10__tanks.sql`, validated on MySQL 8.0.46).
- v2 REST `/api/v2/tanks` (`POST`, `GET`, `GET /{id}`), organization from the principal.

## Tests

`TankModelTest`: register → version 1 and NATIVE classification; out-of-range level rejected; another
organization's customer rejected; reconfiguration bumps to version 2 and converts units;
`TankEligibility` truth table. ArchUnit baseline unchanged.

## Asunciones abiertas

- **A1 — `fuelType` on Tank is a code `String`**, not the inventory `FuelType` enum, to avoid a new
  cross-module domain dependency (ArchUnit baseline must not grow).
- **A2 — the mappable rule is capacity > 0 + fuel type present**; it does not try to distinguish vehicle
  vs. machinery. Adjust when we review the classification with the business.
- **A3 — `classification` is `NATIVE` unless a legacy equipment id was supplied.**
