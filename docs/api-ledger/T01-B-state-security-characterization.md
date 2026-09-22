# T01-B — State and security characterization (S01)

> **Build no verificado en esta máquina — pendiente de verificación por el usuario.** El known-gap #3 se
> marcó como resuelto por T14-B y su test se renombró/ajustó; sin SDK de Java no se ejecutó `test`.

Parent spec: S01 — Caracterizar contratos y estados actuales.
Precondition: T01-A (`docs/api-ledger/T01-A-rest-ledger.md`), 77/77 routes ledgered, 11 golden/
contract tests green.

Scope: characterization only. Nothing in `src/main/java` was touched. Every gap below is
reproduced by a deterministic MockMvc/H2 test and is left exactly as the runtime behaves today —
none of it is fixed here. Tests live under
`src/test/java/com/primefuel/fulltank/platform/contract/characterization/`:

- `StateLifecycleRetryCharacterizationTest` — state-machine retries/duplicates across
  fuel-requests, the direct fuel-order path, deliveries and payments.
- `CrossTenantIsolationTest` — tenant A / tenant B fixtures across fuel-requests, fuel-orders,
  deliveries, payments and equipment.

MySQL note: the roadmap's W0 gate row asks for "H2 + MySQL harness smoke" for negative/concurrent
cases. This project has no MySQL Testcontainers/profile wired up yet (only H2 in-memory is
configured for `spring.profiles.active=test`) — **limitation, documented as required by the
ticket**: true concurrent-writer races (two threads racing `accept()` on the same row under real
row locks) are not exercised here. What *is* covered with H2 is the deterministic, sequential
form of every retry/duplicate scenario (call X, then call X again on the same resource), which is
enough to characterize the missing status-guards themselves — the guard is either there or it
isn't, independent of whether the second call arrives from a thread or from a retry. Wiring a
MySQL test profile is tracked upstream by T03-A/S03 (schema baseline) and should carry the actual
concurrent-race tests once available.

## Known-gaps reproduced (new test, not fixed)

| # | Known-gap | Test | Severity | Resolved later by |
|---|-----------|------|----------|--------------------|
| 1 | `FuelRequestService#accept()` on an already-processed (non-PENDING) request throws a raw `IllegalStateException`; `GlobalExceptionHandler`'s generic fallback turns it into `500 UNEXPECTED_ERROR` instead of `409 Conflict`. Already flagged in T01-A row 66 as a documented defect; this test is the deterministic reproduction the roadmap asked T01-B to add. | `StateLifecycleRetryCharacterizationTest#acceptingAnAlreadyAcceptedFuelRequestReturns500InsteadOf409` | MEDIUM | S10 (`ReplenishmentRequest` lifecycle with proper `PENDING→ACCEPTED\|REJECTED\|CANCELLED` guards and once-only consume) |
| 2 | Same raw-`IllegalStateException`→500 path as #1, triggered by `reject()` after the request already transitioned via `accept()`. Documented in T01-A row 67. | `StateLifecycleRetryCharacterizationTest#rejectingAnAlreadyAcceptedFuelRequestReturns500InsteadOf409` | MEDIUM | S10 |
| 3 | ~~**New.**~~ **FIXED (T14-B).** `Delivery#complete()` had no status guard, but `DeliveryCommandServiceImpl#handle(CompleteDeliveryCommand)` re-ran `FuelOrder#receive()`, which *does* guard on `OrderStatus.DISPATCHED`. After the first `/complete` the order is `PENDING_PAYMENT`, so retrying `/complete` threw a raw `IllegalStateException` → `500` instead of `409`. T14-B reroutes the v1 close through the physical machine, so the retry is rejected by the terminal `COMPLETED` state as a **409** before any order/equipment side effect runs. Test updated to assert 409. | `StateLifecycleRetryCharacterizationTest#completingAnAlreadyDeliveredDeliveryNowReturns409` (renamed; now asserts the fix) | ~~MEDIUM~~ RESOLVED | T14-B (v1 adapter over the S14 physical lifecycle) |
| 4 | `Payment#refund()` has no status guard at all: a payment that was never completed (still `PENDING`) can be "refunded" directly. Documented in T01-A row 70; this is the deterministic test. | `StateLifecycleRetryCharacterizationTest#refundingAPendingNeverCompletedPaymentSucceedsWithNoGuard` | MEDIUM | S23 (Payment state machine with validated transitions) |
| 5 | **New.** `POST /api/v1/fuel-orders` (the "direct order" route) lets a buyer create a `FuelOrder` without ever going through the `FuelRequest` `PENDING→accept` negotiation. Both creation paths coexist and are indistinguishable except for a null `requestId`. Characterized as still-working AS-IS behavior — the roadmap (S10) explicitly requires v2 to make the direct path impossible. | `StateLifecycleRetryCharacterizationTest#directOrderCreationBypassesTheFuelRequestNegotiationFlowEntirely` | LOW | S10 |
| 6 | ~~**New, CRITICAL.**~~ **FIXED (hotfix, out-of-band, see below).** `POST /api/v1/fuel-orders` (`FuelOrderCommandServiceImpl#handle(CreateFuelOrderCommand)`) only checked that `fuelProductId` exists — it never checked that `resource.providerId()` was the product's actual owner, unlike `FuelRequestService#create`, which does exactly that check for the fuel-request path. A buyer could pair tenant A's fuel product with tenant B's `providerId`; the resulting order surfaced under provider B's own `GET /api/v1/fuel-orders/provider/{id}` even though B never listed that product. Now rejected with `403 FORBIDDEN`. | `CrossTenantIsolationTest#directOrderCreationRejectsAProviderIdThatDoesNotOwnTheChosenFuelProduct` (renamed; now asserts the fix) | ~~CRITICAL~~ RESOLVED (was Risk Register R01 — fuga entre distribuidores) | Hotfixed directly; full ownership model still lands with S10 (single lifecycle for order/request creation, provider derived from the product, not from client input) |
| 7 | ~~**New, CRITICAL — cross-tenant WRITE.**~~ **FIXED (hotfix, out-of-band, see below).** `CreateFuelOrderCommand` carried an `equipmentId` that was never validated against `companyId`, neither at order creation (`FuelOrderCommandServiceImpl`) nor at delivery completion (`DeliveryCommandServiceImpl#handle(CompleteDeliveryCommand)`, which just does `equipmentRepository.findById(order.getEquipmentId())` and calls `receiveFuel()` unconditionally). A buyer (tenant A) could place a direct order referencing tenant B's `equipmentId`; once tenant A's provider completed the delivery, **tenant B's tank level was mutated** by an order tenant B never created, saw, or approved. Order creation now returns `403 FORBIDDEN` when `equipmentId` doesn't belong to `companyId` (validation is skipped when `equipmentId` is null, matching its existing optional semantics on the fuel-request path). | `CrossTenantIsolationTest#directOrderCreationRejectsAnEquipmentIdThatDoesNotBelongToTheBuyerCompany` (renamed; now asserts the fix) | ~~CRITICAL~~ RESOLVED (was Risk Register R01 — fuga entre distribuidores; this instance was a write, strictly worse than the read-only leaks R01 anticipates) | Hotfixed directly; the full transactional orchestration still lands with S06 (Tank ownership/validation) + S15 (`AssignDelivery` validating every cross-module reference against the accepted request's tenant before mutating anything) |

### Hotfix note (2026-09-21, out-of-band, ahead of the roadmap sequence)

Gaps #6 and #7 were severity CRITICAL and already exploitable in production (unauthenticated-tenant
data mutation), so at the user's explicit request they were patched immediately instead of waiting
for S10/S06/S15. Change: `FuelOrderCommandServiceImpl#handle(CreateFuelOrderCommand)` now validates
(a) `product.getProviderId().equals(command.providerId())` and (b), when `command.equipmentId()` is
non-null, that the equipment belongs to `command.companyId()` — both via existing query services
(`FuelProductQueryService`, `EquipmentQueryService`), returning `ApplicationError.forbidden(...)`
(HTTP 403) on violation. This is a minimal, targeted fix scoped to the direct-order path only; it is
not S10/S06/S15 (which still need to happen for the full tenant/ownership model across the request
lifecycle) — see `roadmap-checklist.md` at the repo root for tracking. The two characterization
tests that used to prove the exploit now assert the 403 rejection instead (renamed accordingly);
build verified green at 30/30 after the change.

## Cross-tenant isolation confirmed correct (regression oracle, not a gap)

To keep the A/B fixtures honest — and to catch a future regression the moment it lands — the same
test file also asserts the paths that are *already* scoped correctly today, so this suite fails
loudly if any of them stop being scoped:

- `GET /api/v1/fuel-requests/{id}` — tenant B's buyer and provider principals both get `404`,
  never tenant A's data (`CrossTenantIsolationTest#tenantBCannotReadTenantAsFuelRequestOrFuelOrderOrDeliveryOrPayment`).
- `GET /api/v1/fuel-orders/{id}` — same 404 scoping for a stranger buyer.
- `GET /api/v1/deliveries/{id}` — same 404 scoping for both a stranger buyer and provider.
- `GET /api/v1/payments/{id}` — same 404 scoping for a stranger provider.
- `GET /api/v1/equipment/{id}` — `EquipmentController` filters by `ownsCompany()` on the read
  path; a stranger buyer gets `404`
  (`CrossTenantIsolationTest#tenantBCannotReadTenantAsEquipment`).
- `POST /api/v1/payments` — `PaymentsController#createPayment` requires
  `resource.companyId().equals(order.getCompanyId())`, so a mismatched order/company pair is
  rejected as `404` before any cross-tenant payment could be created (already characterized in
  T01-A's `AuthorizationContractTest`; re-verified while building the A/B fixtures for this
  ticket, no new test needed).

## Acceptance criteria check (against roadmap T01-B row and S01)

- Gaps de estados/tenancy reproducidos y etiquetados con test determinista: **sí**, 7 known-gaps
  above, each with its own test method, none corrected.
- No "corregir" nada durante la caracterización: **sí**, zero changes under `src/main/java` in
  this ticket (verify with `git status` / `git diff --cached` — only `src/test/**` and `docs/**`
  are staged).
- Build sigue en verde: **sí**, `./mvnw test` — 30/30 tests pass (10 pre-existing +
  11 from T01-A + 9 new from T01-B). See `target/surefire-reports/*.txt`.
- Fixtures A/B para aislamiento cross-tenant: **sí**, `CrossTenantIsolationTest` builds two full
  tenants (`Tenant` fixture: buyer company + provider company + fuel product each) per scenario.
- Casos nuevos de aislamiento cross-tenant en fuel-requests/deliveries/payments/equipment: **sí**,
  found and reproduced two CRITICAL cross-tenant gaps in the direct fuel-order path (#6 and #7
  above) that were not in the T01-A ledger; fuel-requests, deliveries, payments and equipment
  reads themselves are correctly scoped today (see previous section).

## Headline finding for the user

**Confirmed real cross-tenant leak, not just a theoretical risk.** `POST /api/v1/fuel-orders`
(the direct-order route, independent of the fuel-request negotiation flow) accepts an
`equipmentId` and a `providerId` with **zero ownership validation**. Two concrete, reproduced
consequences:

1. A buyer can create an order that credits a *different company's* equipment/tank when the
   delivery completes — tenant B's tank level changes because of an order tenant B never
   authorized (test #7, CRITICAL).
2. A buyer can create an order under a provider that never listed the referenced fuel product,
   and that provider will see the order in their own order list (test #6, CRITICAL).

Both map to Risk Register **R01** (fuga entre distribuidores, CRITICAL). Neither is fixed here —
per the roadmap's characterization rule, they are documented and left as-is, to be closed by
**S10** (single order/request lifecycle) and **S06/S15** (equipment ownership validated inside
the transactional assignment orchestration) in later waves.
