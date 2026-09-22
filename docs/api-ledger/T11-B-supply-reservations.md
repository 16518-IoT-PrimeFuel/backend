# T11-B — Reserva y conciliación de suministro (S11)

Parent spec: S11. Precondition: T11-A.

## What was added

- Aggregate `SupplyReservation` (`providerId`, `fuelProductId`, `reference`, `quantity`, `unit`,
  `unitPrice`, `status`), statuses `ACTIVE/RELEASED/RECONCILED`. It **snapshots unit price and unit at
  reserve time** so later reconciliation never re-reads a mutated catalog.
- `SupplyReservationService`: `reserve`, `release(reference)`, `reconcile(reference)`. `release` and
  `reconcile` are idempotent (they transition only `ACTIVE` rows; a second call changes nothing and
  still succeeds).
- Availability = catalog stock − sum of `ACTIVE` reservations; a request beyond availability is a
  conflict (no oversell).
- Per-product mutex table `supply_stock_locks` (unique `provider_id, fuel_product_id`) locked with
  `PESSIMISTIC_WRITE` inside the reservation transaction, so concurrent reserves serialize per
  product. The mutex row is created in its own `REQUIRES_NEW` transaction
  (`SupplyStockLockInitializer`) so a creation race cannot poison the reservation transaction.
- Persistence: `supply_reservations` + `supply_stock_locks`
  (`V9__supply_reservations.sql`, validated with Hibernate `validate` on MySQL 8.0.46).

## Tests

`SupplyReservationTest`: reserve → over-reserve rejected → release idempotent (second call reports 0
changes and clears `ACTIVE`) → unknown tenant/product rejected; plus a two-thread race on a product
with stock 10 where each thread reserves 8 — **exactly one succeeds and the active total never
exceeds stock**.

## Not done yet (deferred)

`fulfillment` still writes `fuel_products.availableStock` directly. Rewiring delivery to reserve →
reconcile through this seam is part of the Delivery/Fleet wave (S14/S15, T14/T15) where the physical
lifecycle is separated; doing it now would touch a HIGH-risk flow outside T11-B's scope.

## Asunciones abiertas

- **A1 (U05) — reservation is enabled on the legacy `Double` stock** (see T11-A A1); no decimal
  migration.
- **A2 — `reference` is the correlation key** (the order/request id as text) used to release and
  reconcile. No FK to ordering yet; the bridge arrives in T10-B/T14.
- **A3 — the concurrency proof runs on H2** with a pessimistic lock (MySQL validation covers schema);
  the same invariant must be re-run on MySQL when delivery is rewired.
