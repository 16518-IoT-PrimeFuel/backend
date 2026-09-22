# T11-A — Interfaz Supply y unidades (S11)

Parent spec: S11. Preconditions: T04-B, T03-B. U05 is unresolved → the reservation capability is
activated with an explicit assumption (see below).

## What was added (new module `supply`)

- `supply.domain.valueobjects.Unit` (`LITRE`, `GALLON`, conversion factor) and `Volume`
  (finite, non-negative amount + unit; `convertedTo`, `plus`, `atLeast`).
- Public seam `supply.api.SupplyCatalog`: `findForTenant(providerId, fuelProductId)` /
  `listForTenant(providerId, onlyActive)` returning a `SupplySnapshot` (product id, name, fuel type
  code, unit code, price, stock, active). Other modules read supply only through this seam.
- Adapter `supply.infrastructure.inventory.InventorySupplyCatalog` over
  `inventory.application.queryservices.FuelProductQueryService`, filtering by tenant (`providerId`)
  and by `active`.
- `iam.api.TenantAccess.currentProviderId()` (new method) so supply resolves the tenant from the
  authenticated principal.
- v2 REST `GET /api/v2/products` (+ `/{fuelProductId}`), tenant-scoped, 403 without a provider
  identity. v1 `/fuel-products` untouched.

## Tests

`VolumeTest` (negative/NaN/Infinity rejected, unit conversion), `SupplyCatalogTest` (tenant and
active filtering, unknown product for another tenant is invisible). ArchUnit baseline unchanged.

## Asunciones abiertas

- **A1 (U05) — `stock` is the legacy `Double`, no negatives.** The catalog treats the legacy value as
  a quantity already expressed in the product's declared unit and never negative; no reinterpretation
  or decimal migration was performed.
- **A2 — Tenant is the authenticated provider.** `fuel_products.providerId` is the legacy tenant key;
  the organization→provider mapping is deferred, so supply resolves the provider from the principal
  (same tenancy semantics as v1). Revisit with U05/U01.
- **A3 — Unknown unit codes default to LITRE** (`Unit.fromCode`), since the legacy column is free text.
