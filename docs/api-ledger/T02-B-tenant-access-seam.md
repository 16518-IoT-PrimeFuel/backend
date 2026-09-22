# T02-B — Public interface pilot and architecture baseline (S02)

Parent spec: S02 — Establecer fronteras verificables de módulos.
Precondition: T02-A (`docs/api-ledger/T02-A-module-dependency-rules.md`).
Scope: introduce the first **deep seam** and migrate one real cross-module coupling onto it, so the
frozen architecture baseline shrinks. This is the first `api` package in the codebase.

## The crossing being replaced

Seven modules reach into `iam.infrastructure.authorization.sfs.services.CurrentUserAccess` for
tenant/ownership checks (constructor injection and/or `@PreAuthorize("@currentUserAccess...")`). That
is a compile-time dependency on another module's infrastructure. S02's security note requires exactly
the opposite: "`CurrentAccess` pasa por interfaz pública, no por IAM infrastructure".

## The seam

- **Public contract:** `com.primefuel.fulltank.platform.iam.api.TenantAccess` — a small capability
  interface, scoped to the methods callers actually use (verified by grepping every
  `currentUserAccess.<method>` usage across `src/main`):
  `ownsCompany(Long)`, `ownsProvider(Long)`, `ownsUser(Long)`, `ownsCompanyOrProvider(Long, Long)`,
  `isBuyerRole()`.
- **Implementation (iam-internal):**
  `iam.infrastructure.authorization.sfs.services.TenantAccessImpl`, a `@Component("tenantAccess")` that
  delegates to the existing `CurrentUserAccess` (which stays the Spring-Security adapter and its bean
  name `currentUserAccess` keeps working for the not-yet-migrated callers).
- The interface hides all of it: `SecurityContextHolder`, `UserDetailsImpl`, authority strings and the
  company/provider resolution. Callers now depend on a stable, business-meaningful capability.

## Pilot caller migrated

`inventory.interfaces.rest.FuelProductsController` — was the module whose *only* frozen violations were
the `CurrentUserAccess` coupling (5 lines), so it is the cleanest, lowest-risk pilot:

- constructor/field type `CurrentUserAccess` → `iam.api.TenantAccess`;
- direct calls `currentUserAccess.isBuyerRole()/ownsProvider(...)` → `tenantAccess...`;
- SpEL `@currentUserAccess.*` → `@tenantAccess.*` (so the pilot is decoupled from iam's internal bean
  name too, not just from the type).

Behaviour is unchanged — `TenantAccessImpl` is a pure delegation to `CurrentUserAccess`.

## Effect on the frozen baseline

| | Before T02-B | After T02-B |
|---|---:|---:|
| inventory | 5 | **0** |
| Total frozen violations | 185 | **180** |

`FreezingArchRule` removed the solved inventory entries automatically on the next green run (the store
only ever shrinks); no new violation was introduced. If `inventory` were to reach into `iam`'s
internals again, it would now be a *new* violation and fail the build.

Per-module baseline after this ticket: `catalog 6, equipment 26, fulfillment 47, iam 0, inventory 0,
notification 17, ordering 27, payment 16, reporting 41`.

## Tests

- **Architecture:** `ModuleBoundaryRulesTest` — baseline shrank and stayed green (180 violations),
  seeded-violation and `shared` rules unaffected.
- **Integration:** `AuthorizationContractTest`, `OrderFulfillmentGoldenPathTest` and both
  characterization suites already drive `/api/v1/fuel-products` through the real Spring context, so
  the new `tenantAccess` bean wiring is exercised end-to-end. Full suite: **33/33 green**.

## Acceptance criteria check (Roadmap T02-B / S02)

- Primer caller usa interface pública: **yes** (`FuelProductsController` → `iam.api.TenantAccess`).
- Baseline baja (y no crece): **yes** — 185 → 180, no additions.
- Probar módulo: **yes** — architecture test + existing fuel-products integration coverage, 33/33.
- Conservar package raíz: **yes** — `com.primefuel.fulltank.platform.iam.api` added under the root;
  no package renames.

## Notes / follow-ups

- The other six modules still import `CurrentUserAccess` directly; they remain in the baseline and
  should be migrated onto `TenantAccess` in later tickets (each migration shrinks the baseline).
- `@PreAuthorize("@currentUserAccess...")` in the not-yet-migrated modules is a *runtime* bean-name
  coupling that ArchUnit cannot see; it disappears as those modules move to `@tenantAccess`.
- The target end-state (modules depend on other modules only via `api`/`events`) is not reached yet;
  this ticket only establishes the first `api` surface and proves the mechanism works.
