# T10-B — Puente FuelRequest/FuelOrder compatible (S10, cierra W3)

Parent spec: S10. Precondition: T10-A.

## What was added

- `LegacyFuelRequestBridge` (ordering) routes the v1 fuel-request flow through the `replenishment`
  module while preserving legacy identity:
  - **create**: creates the legacy `fuel_requests` row (unchanged) and a linked `ReplenishmentRequest`
    correlated by the episode key `fuel-request:{legacyId}`, resolving organization/customer from the
    customer mapping (`equipment.api.CustomerDirectory`) and the tank from
    `equipment.api.TankAssets` (falling back to the legacy `buyerCompanyId` when no customer is mapped).
  - **accept**: accepts the linked request and **consumes its acceptance exactly once** before creating
    the order; the order keeps the legacy `requestId`, and the resulting `orderId` is attached to the
    replenishment request. A second accept fails, so no duplicate order can be produced.
  - **reject**: propagates the decision to the linked request.
  - Legacy rows with no linked request keep working unchanged (rollback path).
- `FuelRequestsController` delegates create/accept/reject to the bridge; the reads still use
  `FuelRequestService`.
- **v2 direct order creation does not exist**: the only v2 surface is
  `/api/v2/replenishment-requests`, which produces no `FuelOrder`; orders still arise only from an
  accepted review (S10's "orden directa v2 imposible").

No new schema in this ticket.

## Architecture-baseline note (explicit delta)

The frozen ArchUnit baseline grew by **one** entry (180 → 181):
`Constructor <FuelRequestService.<init>(...)> has parameter of type <FuelProductRepository>`.
This is a **pre-existing** dependency of `FuelRequestService` on `inventory.domain` (the same pair was
already frozen as a field dependency), which ArchUnit re-reported under its constructor-parameter form
once the module gained a new class. No new cross-module coupling was introduced. To avoid touching the
constructors of classes that already carry frozen dependencies, the bridge collaborators are
field-injected into the legacy service/controller, and the bridge itself lives in a new component.

## Tests

`LegacyFuelRequestBridgeTest`: a v1 request creates a linked replenishment (episode key, organization);
accepting creates the order with the legacy `requestId`, leaves the replenishment `ACCEPTED` with the
order attached, and a second accept throws (no second order); rejecting propagates `REJECTED`.

## Asunciones abiertas

- **A1 — `organizationId` falls back to the legacy `buyerCompanyId`** when the buyer company has no
  mapped customer yet (rows still awaiting the T05-B backfill).
- **A2 — `customer_account_id` is nullable** on `replenishment_requests` so legacy-originated requests
  can exist before a customer is mapped (V11 adjusted accordingly and re-validated on MySQL 8.0.46).
- **A3 — the ArchUnit baseline delta above is accepted.** If we prefer a strict "baseline never grows"
  rule, the alternative is to reorder/annotate `FuelRequestService` so ArchUnit keeps reporting the
  field-based dependency — to be decided together.
