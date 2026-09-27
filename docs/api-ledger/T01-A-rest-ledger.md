# T01-A — REST baseline ledger (S01)

Parent spec: S01 — Caracterizar contratos y estados actuales.
Scope: characterization only. Nothing here was fixed; every defect found while reconciling the
64 mappings is recorded as a `known-gap` and left exactly as the runtime behaves today.

How this was built: `grep -rn "@GetMapping\|@PostMapping\|@PutMapping\|@PatchMapping\|@DeleteMapping"`
across `src/main/java/**/interfaces/rest/*Controller.java`, combined with each class's
`@RequestMapping` base path and `@PreAuthorize` annotations (or the inline `CurrentUserAccess`
checks used instead of `@PreAuthorize` on several endpoints). Cross-checked against the live
`RequestMappingHandlerMapping` registrations by `ApiLedgerSelfCheckTest`, and against the
springdoc `/api-docs` document by `OpenApiSnapshotTest`. 3 of the 21 controller classes under
`interfaces/rest` (`FulfillmentController`, `DirectoryController`, `InventoryController`,
`NotificationController`, `OrderingController`, `PaymentController`) were empty placeholder
classes with zero `@RequestMapping` methods — they contributed 0 operations and are not counted
below. **T24-A resolved this known-gap: all six were deleted** (verified empty and unreferenced).
The ledger now has 64 active v1 operations after the T24-B product retirements.

Auth column legend: `public` = matched by `permitAll()` in `WebSecurityConfiguration`;
`@PreAuthorize(expr)` = declared on the method; `manual: <check>` = no `@PreAuthorize`, the
controller performs the equivalent ownership check in code and returns 404/403/400 itself;
`authenticated` = no method-level restriction beyond the global `anyRequest().authenticated()`.

Consumer column: `UNKNOWN` unless there is direct evidence in this repo of a caller (there is no
sibling frontend/mobile repo checked out alongside this backend to inspect).

| # | Method | Path | Controller | Auth | Status codes | Consumer | Notes / known-gap |
|---|--------|------|------------|------|---------------|----------|--------------------|
| 1 | POST | `/api/v1/equipment/{equipmentId}/favorite-provider` | EquipmentController | `@PreAuthorize` isBuyerRole() | 200, 404 | UNKNOWN | Ownership of the equipment itself is checked manually via `equipmentRepository` + `ownsCompany`, on top of the role check. |
| 2 | POST | `/api/v1/equipment` | EquipmentController | `@PreAuthorize` ownsCompany(resource.companyId) | 201 | UNKNOWN | |
| 3 | POST | `/api/v1/equipment/{equipmentId}/update` | EquipmentController | manual: ownsCompany(existing.companyId) | 200, 404 | UNKNOWN | **known-gap**: update is a `POST .../update` action route instead of `PUT /api/v1/equipment/{id}`, inconsistent with the PUT-style updates used elsewhere (fuel-products, drivers, vehicles, buyer/provider companies). |
| 4 | GET | `/api/v1/equipment` | EquipmentController | `@PreAuthorize` hasAuthority('ROLE_ADMIN') | 200 | UNKNOWN | |
| 5 | GET | `/api/v1/equipment/{equipmentId}` | EquipmentController | manual: filter ownsCompany(equipment.companyId) | 200, 404 | UNKNOWN | |
| 6 | GET | `/api/v1/equipment/company/{companyId}` | EquipmentController | `@PreAuthorize` ownsCompany(companyId) | 200 | UNKNOWN | |
| 7 | POST | `/api/v1/deliveries` | DeliveriesController | `@PreAuthorize` ownsProvider(resource.providerId) | 201, 404, 409 | UNKNOWN | **known-gap**: creating a delivery immediately calls `Delivery#dispatch()` and `FuelOrder#dispatch()` inside the command handler — a delivery is born in `DISPATCHED` status, never `SCHEDULED`, despite the aggregate modeling a `SCHEDULED` initial state. |
| 8 | POST | `/api/v1/deliveries/{deliveryId}/dispatch` | DeliveriesController | manual: ownsProvider(delivery.providerId) | 200, 404 | UNKNOWN | **known-gap**: given note on row 10, this endpoint is a same-state no-op in the current happy path — `Delivery#dispatch()` has no status guard, so it silently re-stamps `dispatchedAt` instead of rejecting an invalid transition. |
| 9 | POST | `/api/v1/deliveries/{deliveryId}/complete` | DeliveriesController | manual: ownsProvider(delivery.providerId) | 200, 404 | UNKNOWN | Also transitions the parent `FuelOrder` to `PENDING_PAYMENT` and, if the order has an `equipmentId`, calls `Equipment#receiveFuel` — a cross-aggregate write inside the same transaction. |
| 10 | POST | `/api/v1/deliveries/{deliveryId}/fail` | DeliveriesController | manual: ownsProvider(delivery.providerId) | 200, 404 | UNKNOWN | `Delivery#fail(reason)` has no status guard either; does not roll back driver/vehicle/stock changes made at creation time. |
| 11 | GET | `/api/v1/deliveries` | DeliveriesController | `@PreAuthorize` hasAuthority('ROLE_ADMIN') | 200 | UNKNOWN | |
| 12 | GET | `/api/v1/deliveries/provider/{providerId}` | DeliveriesController | `@PreAuthorize` ownsProvider(providerId) | 200 | UNKNOWN | |
| 13 | GET | `/api/v1/deliveries/{deliveryId}` | DeliveriesController | manual: ownsProvider(delivery.providerId) or ownsCompany(order.companyId) | 200, 404 | UNKNOWN | |
| 14 | GET | `/api/v1/deliveries/order/{orderId}` | DeliveriesController | manual: same as row 16 | 200, 404 | UNKNOWN | |
| 15 | POST | `/api/v1/authentication/sign-up` | AuthenticationController | public | 201, 400 | UNKNOWN | Exactly one of `buyerCompany`/`providerCompany` required; validated in `UserCommandServiceImpl`. |
| 16 | POST | `/api/v1/authentication/sign-in` | AuthenticationController | public | 200, 400, 404 | UNKNOWN | **known-gap**: unknown username → 404 (`USER_NOT_FOUND`), wrong password → 400 (`VALIDATION_ERROR`). Different codes for the two failure modes leak whether a username is registered, and neither is the conventional 401. |
| 17 | POST | `/api/v1/authentication/password-reset/request` | AuthenticationController | public | 202 | UNKNOWN | Always 202 regardless of whether the account exists — deliberately non-revealing, unlike sign-in (row 29). |
| 18 | POST | `/api/v1/authentication/password-reset/confirm` | AuthenticationController | public | 204, 400 | UNKNOWN | Token is single-use (400 on reuse), validated by `FullTankPlatformApplicationTests#passwordResetIsDeliveredOnceAndChangesThePassword`. |
| 19 | POST | `/api/v1/buyer-companies` | BuyerCompaniesController | public (`permitAll` on this exact path) | 201 | UNKNOWN | **known-gap** (tracked by roadmap spec S04): creates a buyer company shell with no associated user account; anyone unauthenticated can call this. |
| 20 | GET | `/api/v1/buyer-companies` | BuyerCompaniesController | `@PreAuthorize` hasAuthority('ROLE_ADMIN') | 200 | UNKNOWN | |
| 21 | GET | `/api/v1/buyer-companies/{companyId}` | BuyerCompaniesController | `@PreAuthorize` ownsCompany(companyId) | 200, 404 | UNKNOWN | |
| 22 | PUT | `/api/v1/buyer-companies/{companyId}` | BuyerCompaniesController | `@PreAuthorize` ownsCompany(companyId) | 200, 404 | UNKNOWN | |
| 23 | POST | `/api/v1/provider-companies` | ProviderCompaniesController | public (`permitAll` on this exact path) | 201 | UNKNOWN | Same known-gap as row 32, mirrored for providers. |
| 24 | GET | `/api/v1/provider-companies` | ProviderCompaniesController | `@PreAuthorize` isBuyerRole() | 200 | UNKNOWN | Providers cannot list other providers; only buyers (browsing the marketplace) and admins-as-buyers can. |
| 25 | GET | `/api/v1/provider-companies/{providerId}` | ProviderCompaniesController | `@PreAuthorize` isBuyerRole() or ownsProvider(providerId) | 200, 404 | UNKNOWN | |
| 26 | PUT | `/api/v1/provider-companies/{providerId}` | ProviderCompaniesController | `@PreAuthorize` ownsProvider(providerId) | 200, 404 | UNKNOWN | |
| 27 | GET | `/api/v1/users` | UsersController | `@PreAuthorize` hasAuthority('ROLE_ADMIN') | 200 | UNKNOWN | |
| 28 | GET | `/api/v1/users/{userId}` | UsersController | `@PreAuthorize` ownsUser(userId) | 200, 404 | UNKNOWN | |
| 29 | POST | `/api/v1/fuel-products` | FuelProductsController | `@PreAuthorize` ownsProvider(resource.providerId) | 201 | UNKNOWN | |
| 30 | POST | `/api/v1/fuel-products/{fuelProductId}/update-stock` | FuelProductsController | manual: ownsProvider(product.providerId) | 200, 404 | UNKNOWN | **known-gap**: another `POST .../action` route instead of a dedicated `PATCH`/`PUT`, same inconsistency as row 6. |
| 31 | GET | `/api/v1/fuel-products` | FuelProductsController | `@PreAuthorize` isBuyerRole() | 200 | UNKNOWN | Returns every provider's products; providers cannot call this endpoint at all (see row 46 for the provider-scoped alternative). |
| 32 | GET | `/api/v1/fuel-products/{fuelProductId}` | FuelProductsController | manual: isBuyerRole() or ownsProvider(product.providerId) | 200, 404 | UNKNOWN | |
| 33 | GET | `/api/v1/fuel-products/provider/{providerId}` | FuelProductsController | `@PreAuthorize` isBuyerRole() or ownsProvider(providerId) | 200 | UNKNOWN | |
| 34 | PUT | `/api/v1/fuel-products/{fuelProductId}` | FuelProductsController | manual: ownsProvider(product.providerId) | 200, 404 | UNKNOWN | |
| 35 | DELETE | `/api/v1/fuel-products/{fuelProductId}` | FuelProductsController | manual: ownsProvider(product.providerId) | 204, 404 | UNKNOWN | **known-gap**: no check for open fuel-requests/orders referencing this product before hard-deleting it. |
| 36 | POST | `/api/v1/notifications` | NotificationsController | `@PreAuthorize` ownsUser(resource.userId) or ownsCompany(resource.companyId) or ownsProvider(resource.providerId) | 201, 400, 404 | UNKNOWN | Requires exactly one of userId/companyId/providerId; company/provider variants are resolved to a single target user via `UserRepository`. |
| 37 | POST | `/api/v1/notifications/{notificationId}/mark-as-read` | NotificationsController | manual: ownsUser(notification.userId) | 200, 404 | UNKNOWN | |
| 38 | GET | `/api/v1/notifications/{notificationId}` | NotificationsController | manual: ownsUser(notification.userId) | 200, 404 | UNKNOWN | |
| 39 | GET | `/api/v1/notifications/user/{userId}` | NotificationsController | `@PreAuthorize` ownsUser(userId) | 200 | UNKNOWN | |
| 40 | GET | `/api/v1/notifications/buyer/{companyId}` | NotificationsController | `@PreAuthorize` ownsCompany(companyId) | 200 | UNKNOWN | **known-gap**: internally calls `this.getNotificationsByUser(...)` as a plain Java method call, which bypasses that method's own `@PreAuthorize` (no proxy involved). Not currently exploitable because this endpoint already re-checks `ownsCompany` first, but the pattern is fragile if either method's authorization logic changes independently. |
| 41 | GET | `/api/v1/notifications/provider/{providerId}` | NotificationsController | `@PreAuthorize` ownsProvider(providerId) | 200 | UNKNOWN | Same internal-call gap as row 53. |
| 42 | GET | `/api/v1/notifications/user/{userId}/unread` | NotificationsController | `@PreAuthorize` ownsUser(userId) | 200 | UNKNOWN | |
| 43 | POST | `/api/v1/fuel-orders` | FuelOrdersController | `@PreAuthorize` ownsCompany(resource.companyId) | 201 | UNKNOWN | Direct order creation, independent of the fuel-request flow (rows 63–67). |
| 44 | POST | `/api/v1/fuel-orders/{orderId}/confirm` | FuelOrdersController | manual: ownsCompany(order.companyId) | 200, 404 | UNKNOWN | **known-gap**: `FuelOrder#confirm()` has no status guard (unlike `dispatch()`/`receive()`), so it can be called from any status including `CANCELLED`/`PAID`, and it moves the order into `CONFIRMED`, a status the delivery-dispatch guard (`PENDING` only) never expects — confirming an order before creating its delivery effectively strands it. Characterized in `OrderFulfillmentGoldenPathTest#confirmingAnOrderHasNoStateGuardAndCollidesWithTheDispatchFlow`. |
| 45 | POST | `/api/v1/fuel-orders/{orderId}/cancel` | FuelOrdersController | manual: ownsCompanyOrProvider(order.companyId, order.providerId) | 200, 404 | UNKNOWN | **known-gap**: `FuelOrder#cancel()` also has no status guard, and cancelling does not stop a payment from later being created against the same order (see row 68's note) — characterized in `OrderFulfillmentGoldenPathTest#cancellingAnOrderDoesNotBlockCreatingAPaymentForIt`. |
| 46 | GET | `/api/v1/fuel-orders` | FuelOrdersController | `@PreAuthorize` hasAuthority('ROLE_ADMIN') | 200 | UNKNOWN | |
| 47 | GET | `/api/v1/fuel-orders/{orderId}` | FuelOrdersController | manual: ownsCompanyOrProvider(order.companyId, order.providerId) | 200, 404 | UNKNOWN | |
| 48 | GET | `/api/v1/fuel-orders/company/{companyId}` | FuelOrdersController | `@PreAuthorize` ownsCompany(companyId) | 200 | UNKNOWN | |
| 49 | GET | `/api/v1/fuel-orders/provider/{providerId}` | FuelOrdersController | `@PreAuthorize` ownsProvider(providerId) | 200 | UNKNOWN | |
| 50 | POST | `/api/v1/fuel-requests` | FuelRequestsController | `@PreAuthorize` ownsCompany(resource.buyerCompanyId) | 201, 400 | UNKNOWN | |
| 51 | GET | `/api/v1/fuel-requests` | FuelRequestsController | manual: exactly one of `buyerCompanyId`/`providerId`, and caller must own it | 200, 400, 403 | UNKNOWN | **known-gap**: requires exactly one of the two query params — an admin (or a user with no ownership at all) cannot call this endpoint at all; both-present and both-absent are rejected identically as 400/403 depending on which branch is hit, and the 403 has no response body (`ResponseEntity.status(403).build()`), unlike every other denial path in this controller which returns 404. |
| 52 | GET | `/api/v1/fuel-requests/{requestId}` | FuelRequestsController | manual: ownsCompanyOrProvider(request.buyerCompanyId, request.providerId) | 200, 404 | UNKNOWN | |
| 53 | POST | `/api/v1/fuel-requests/{requestId}/accept` | FuelRequestsController | manual: ownsProvider(request.providerId) | 200, 404, 500 | UNKNOWN | **known-gap**: `FuelRequestService.accept()` throws a plain `IllegalStateException` ("Only pending requests can be accepted") when the request is not `PENDING`. `GlobalExceptionHandler` has no specific handler for `IllegalStateException`, so it falls through to the generic `RuntimeException` handler and comes back as `500 UNEXPECTED_ERROR` — not the `Result<T, ApplicationError>` pattern (with a proper 409) used by every command service elsewhere in the codebase. |
| 54 | POST | `/api/v1/fuel-requests/{requestId}/reject` | FuelRequestsController | manual: ownsProvider(request.providerId) | 200, 404, 400, 500 | UNKNOWN | Same `IllegalStateException` → 500 gap as row 66 for an already-processed request. A blank/missing `reason` throws `IllegalArgumentException`, which `GlobalExceptionHandler` does map to a clean 400. |
| 55 | POST | `/api/v1/payments` | PaymentsController | `@PreAuthorize` ownsCompany(resource.companyId) | 201, 400, 404 | UNKNOWN | **known-gap**: only checks that the order exists and that `amount` equals `order.totalPrice` — never checks `order.status`. A payment can be created against a `CANCELLED` order (see row 58's note); characterized in `OrderFulfillmentGoldenPathTest#cancellingAnOrderDoesNotBlockCreatingAPaymentForIt`. |
| 56 | POST | `/api/v1/payments/{paymentId}/complete` | PaymentsController | manual: ownsCompany(payment.companyId) or ownsProvider(order.providerId) | 200, 404 | UNKNOWN | Also calls `FuelOrder#markPaid()`, which only refuses a `CANCELLED` order — completing a payment created against a cancelled order (row 68) is itself blocked here, but the inconsistent order/payment pairing was already allowed to exist. |
| 57 | POST | `/api/v1/payments/{paymentId}/refund` | PaymentsController | manual: same as row 69 | 200, 404 | UNKNOWN | `Payment#refund()` has no status guard — a `PENDING` (never completed) payment can be "refunded". |
| 58 | GET | `/api/v1/payments` | PaymentsController | `@PreAuthorize` hasAuthority('ROLE_ADMIN') | 200 | UNKNOWN | |
| 59 | GET | `/api/v1/payments/{paymentId}` | PaymentsController | manual: ownsCompany(payment.companyId) or ownsProvider(order.providerId) | 200, 404 | UNKNOWN | |
| 60 | GET | `/api/v1/payments/order/{orderId}` | PaymentsController | manual: same as row 72 | 200, 404 | UNKNOWN | |
| 61 | GET | `/api/v1/payments/company/{companyId}` | PaymentsController | `@PreAuthorize` ownsCompany(companyId) | 200 | UNKNOWN | |
| 62 | GET | `/api/v1/analytics/platform` | AnalyticsController | `@PreAuthorize` hasAuthority('ROLE_ADMIN') | 200 | UNKNOWN | |
| 63 | GET | `/api/v1/analytics/providers/{providerId}` | AnalyticsController | `@PreAuthorize` ownsProvider(providerId) | 200 | UNKNOWN | |
| 64 | GET | `/api/v1/analytics/buyers/{companyId}` | AnalyticsController | `@PreAuthorize` ownsCompany(companyId) | 200 | UNKNOWN | |

## Resumen de brechas conocidas (no corregidas en T01-A)

1. Action-style `POST .../update` and `POST .../update-stock` routes remain in equipment and inventory.
2. The legacy delivery lifecycle can create a delivery as `DISPATCHED`; `/dispatch` can be a no-op and `dispatch()`/`fail()` lack status guards.
3. Sign-in distinguishes unknown usernames (404) from wrong passwords (400).
4. Buyer/provider company creation remains public without a linked user account (tracked by S04).
5. Legacy fuel-request list/decision behavior still has authorization and error-mapping gaps; see the three live fuel-request rows above. The family remains until the v2 acceptance flow can create and attach its order atomically.
6. Fuel-order confirm/cancel and payment refund lack status guards; payment creation does not check order status.

Decision: the v1 driver, vehicle, and provider-rating operations were retired by product decision on 2026-09-26. Driver and tanker lifecycle continues through the v2 fleet controllers; provider ratings have no replacement.
