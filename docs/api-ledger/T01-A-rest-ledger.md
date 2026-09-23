# T01-A — REST baseline ledger (S01)

Parent spec: S01 — Caracterizar contratos y estados actuales.
Scope: characterization only. Nothing here was fixed; every defect found while reconciling the
77 mappings is recorded as a `known-gap` and left exactly as the runtime behaves today.

How this was built: `grep -rn "@GetMapping\|@PostMapping\|@PutMapping\|@PatchMapping\|@DeleteMapping"`
across `src/main/java/**/interfaces/rest/*Controller.java`, combined with each class's
`@RequestMapping` base path and `@PreAuthorize` annotations (or the inline `CurrentUserAccess`
checks used instead of `@PreAuthorize` on several endpoints). Cross-checked against the live
`RequestMappingHandlerMapping` registrations by `ApiLedgerSelfCheckTest`, and against the
springdoc `/api-docs` document by `OpenApiSnapshotTest`. 3 of the 21 controller classes under
`interfaces/rest` (`FulfillmentController`, `DirectoryController`, `InventoryController`,
`NotificationController`, `OrderingController`, `PaymentController`) were empty placeholder
classes with zero `@RequestMapping` methods — they contributed 0 operations and are not counted
below. **T24-A resolved this known-gap: all six were deleted** (verified empty and unreferenced);
the live mapping count below (77) is unchanged.

Auth column legend: `public` = matched by `permitAll()` in `WebSecurityConfiguration`;
`@PreAuthorize(expr)` = declared on the method; `manual: <check>` = no `@PreAuthorize`, the
controller performs the equivalent ownership check in code and returns 404/403/400 itself;
`authenticated` = no method-level restriction beyond the global `anyRequest().authenticated()`.

Consumer column: `UNKNOWN` unless there is direct evidence in this repo of a caller (there is no
sibling frontend/mobile repo checked out alongside this backend to inspect).

| # | Method | Path | Controller | Auth | Status codes | Consumer | Notes / known-gap |
|---|--------|------|------------|------|---------------|----------|--------------------|
| 1 | GET | `/api/v1/provider-ratings` | ProviderRatingsController | authenticated | 200 | UNKNOWN | **known-gap**: no `@PreAuthorize` and no server-side scoping — any authenticated user (buyer or provider) can list ratings platform-wide; `companyId`/`providerId` query params are optional filters, not an authorization boundary. |
| 2 | POST | `/api/v1/provider-ratings` | ProviderRatingsController | `@PreAuthorize` ownsCompany(resource.companyId) | 201, 400, 409 | UNKNOWN | One rating per (company, provider) pair enforced in the controller (409 on duplicate). |
| 3 | PUT | `/api/v1/provider-ratings/{id}` | ProviderRatingsController | `@PreAuthorize` ownsCompany(resource.companyId) | 200, 400, 404 | UNKNOWN | companyId/providerId on the resource must match the existing row; rating range 1–5 validated in-controller. |
| 4 | POST | `/api/v1/equipment/{equipmentId}/favorite-provider` | EquipmentController | `@PreAuthorize` isBuyerRole() | 200, 404 | UNKNOWN | Ownership of the equipment itself is checked manually via `equipmentRepository` + `ownsCompany`, on top of the role check. |
| 5 | POST | `/api/v1/equipment` | EquipmentController | `@PreAuthorize` ownsCompany(resource.companyId) | 201 | UNKNOWN | |
| 6 | POST | `/api/v1/equipment/{equipmentId}/update` | EquipmentController | manual: ownsCompany(existing.companyId) | 200, 404 | UNKNOWN | **known-gap**: update is a `POST .../update` action route instead of `PUT /api/v1/equipment/{id}`, inconsistent with the PUT-style updates used elsewhere (fuel-products, drivers, vehicles, buyer/provider companies). |
| 7 | GET | `/api/v1/equipment` | EquipmentController | `@PreAuthorize` hasAuthority('ROLE_ADMIN') | 200 | UNKNOWN | |
| 8 | GET | `/api/v1/equipment/{equipmentId}` | EquipmentController | manual: filter ownsCompany(equipment.companyId) | 200, 404 | UNKNOWN | |
| 9 | GET | `/api/v1/equipment/company/{companyId}` | EquipmentController | `@PreAuthorize` ownsCompany(companyId) | 200 | UNKNOWN | |
| 10 | POST | `/api/v1/deliveries` | DeliveriesController | `@PreAuthorize` ownsProvider(resource.providerId) | 201, 404, 409 | UNKNOWN | **known-gap**: creating a delivery immediately calls `Delivery#dispatch()` and `FuelOrder#dispatch()` inside the command handler — a delivery is born in `DISPATCHED` status, never `SCHEDULED`, despite the aggregate modeling a `SCHEDULED` initial state. |
| 11 | POST | `/api/v1/deliveries/{deliveryId}/dispatch` | DeliveriesController | manual: ownsProvider(delivery.providerId) | 200, 404 | UNKNOWN | **known-gap**: given note on row 10, this endpoint is a same-state no-op in the current happy path — `Delivery#dispatch()` has no status guard, so it silently re-stamps `dispatchedAt` instead of rejecting an invalid transition. |
| 12 | POST | `/api/v1/deliveries/{deliveryId}/complete` | DeliveriesController | manual: ownsProvider(delivery.providerId) | 200, 404 | UNKNOWN | Also transitions the parent `FuelOrder` to `PENDING_PAYMENT` and, if the order has an `equipmentId`, calls `Equipment#receiveFuel` — a cross-aggregate write inside the same transaction. |
| 13 | POST | `/api/v1/deliveries/{deliveryId}/fail` | DeliveriesController | manual: ownsProvider(delivery.providerId) | 200, 404 | UNKNOWN | `Delivery#fail(reason)` has no status guard either; does not roll back driver/vehicle/stock changes made at creation time. |
| 14 | GET | `/api/v1/deliveries` | DeliveriesController | `@PreAuthorize` hasAuthority('ROLE_ADMIN') | 200 | UNKNOWN | |
| 15 | GET | `/api/v1/deliveries/provider/{providerId}` | DeliveriesController | `@PreAuthorize` ownsProvider(providerId) | 200 | UNKNOWN | |
| 16 | GET | `/api/v1/deliveries/{deliveryId}` | DeliveriesController | manual: ownsProvider(delivery.providerId) or ownsCompany(order.companyId) | 200, 404 | UNKNOWN | |
| 17 | GET | `/api/v1/deliveries/order/{orderId}` | DeliveriesController | manual: same as row 16 | 200, 404 | UNKNOWN | |
| 18 | GET | `/api/v1/drivers` | DriversController | `@PreAuthorize` ownsProvider(providerId) | 200 | UNKNOWN | `providerId` is a required `@RequestParam`. |
| 19 | GET | `/api/v1/drivers/{id}` | DriversController | manual: ownsProvider(driver.providerId) | 200, 404 | UNKNOWN | |
| 20 | POST | `/api/v1/drivers` | DriversController | `@PreAuthorize` ownsProvider(resource.providerId) | 201 | UNKNOWN | `status` defaults to `AVAILABLE` when blank. |
| 21 | PUT | `/api/v1/drivers/{id}` | DriversController | manual: ownsProvider(driver.providerId) and ownsProvider(new providerId) | 200, 404 | UNKNOWN | Allows re-assigning a driver to a different provider the caller also owns; no invariant stops orphaning active deliveries referencing this driver. |
| 22 | DELETE | `/api/v1/drivers/{id}` | DriversController | manual: ownsProvider(driver.providerId) | 204, 404 | UNKNOWN | **known-gap**: no check for in-flight deliveries referencing the driver before hard-deleting the row. |
| 23 | GET | `/api/v1/vehicles` | VehiclesController | `@PreAuthorize` ownsProvider(providerId) | 200 | UNKNOWN | `providerId` is a required `@RequestParam`. |
| 24 | GET | `/api/v1/vehicles/{id}` | VehiclesController | manual: ownsProvider(vehicle.providerId) | 200, 404 | UNKNOWN | |
| 25 | POST | `/api/v1/vehicles` | VehiclesController | `@PreAuthorize` ownsProvider(resource.providerId) | 201 | UNKNOWN | `status` defaults to `AVAILABLE`, `unit` defaults to `LITERS` when blank. |
| 26 | PUT | `/api/v1/vehicles/{id}` | VehiclesController | manual: ownsProvider(vehicle.providerId) and ownsProvider(new providerId) | 200, 404 | UNKNOWN | Same re-assignment gap as row 21. |
| 27 | DELETE | `/api/v1/vehicles/{id}` | VehiclesController | manual: ownsProvider(vehicle.providerId) | 204, 404 | UNKNOWN | Same in-flight-delivery gap as row 22. |
| 28 | POST | `/api/v1/authentication/sign-up` | AuthenticationController | public | 201, 400 | UNKNOWN | Exactly one of `buyerCompany`/`providerCompany` required; validated in `UserCommandServiceImpl`. |
| 29 | POST | `/api/v1/authentication/sign-in` | AuthenticationController | public | 200, 400, 404 | UNKNOWN | **known-gap**: unknown username → 404 (`USER_NOT_FOUND`), wrong password → 400 (`VALIDATION_ERROR`). Different codes for the two failure modes leak whether a username is registered, and neither is the conventional 401. |
| 30 | POST | `/api/v1/authentication/password-reset/request` | AuthenticationController | public | 202 | UNKNOWN | Always 202 regardless of whether the account exists — deliberately non-revealing, unlike sign-in (row 29). |
| 31 | POST | `/api/v1/authentication/password-reset/confirm` | AuthenticationController | public | 204, 400 | UNKNOWN | Token is single-use (400 on reuse), validated by `FullTankPlatformApplicationTests#passwordResetIsDeliveredOnceAndChangesThePassword`. |
| 32 | POST | `/api/v1/buyer-companies` | BuyerCompaniesController | public (`permitAll` on this exact path) | 201 | UNKNOWN | **known-gap** (tracked by roadmap spec S04): creates a buyer company shell with no associated user account; anyone unauthenticated can call this. |
| 33 | GET | `/api/v1/buyer-companies` | BuyerCompaniesController | `@PreAuthorize` hasAuthority('ROLE_ADMIN') | 200 | UNKNOWN | |
| 34 | GET | `/api/v1/buyer-companies/{companyId}` | BuyerCompaniesController | `@PreAuthorize` ownsCompany(companyId) | 200, 404 | UNKNOWN | |
| 35 | PUT | `/api/v1/buyer-companies/{companyId}` | BuyerCompaniesController | `@PreAuthorize` ownsCompany(companyId) | 200, 404 | UNKNOWN | |
| 36 | POST | `/api/v1/provider-companies` | ProviderCompaniesController | public (`permitAll` on this exact path) | 201 | UNKNOWN | Same known-gap as row 32, mirrored for providers. |
| 37 | GET | `/api/v1/provider-companies` | ProviderCompaniesController | `@PreAuthorize` isBuyerRole() | 200 | UNKNOWN | Providers cannot list other providers; only buyers (browsing the marketplace) and admins-as-buyers can. |
| 38 | GET | `/api/v1/provider-companies/{providerId}` | ProviderCompaniesController | `@PreAuthorize` isBuyerRole() or ownsProvider(providerId) | 200, 404 | UNKNOWN | |
| 39 | PUT | `/api/v1/provider-companies/{providerId}` | ProviderCompaniesController | `@PreAuthorize` ownsProvider(providerId) | 200, 404 | UNKNOWN | |
| 40 | GET | `/api/v1/users` | UsersController | `@PreAuthorize` hasAuthority('ROLE_ADMIN') | 200 | UNKNOWN | |
| 41 | GET | `/api/v1/users/{userId}` | UsersController | `@PreAuthorize` ownsUser(userId) | 200, 404 | UNKNOWN | |
| 42 | POST | `/api/v1/fuel-products` | FuelProductsController | `@PreAuthorize` ownsProvider(resource.providerId) | 201 | UNKNOWN | |
| 43 | POST | `/api/v1/fuel-products/{fuelProductId}/update-stock` | FuelProductsController | manual: ownsProvider(product.providerId) | 200, 404 | UNKNOWN | **known-gap**: another `POST .../action` route instead of a dedicated `PATCH`/`PUT`, same inconsistency as row 6. |
| 44 | GET | `/api/v1/fuel-products` | FuelProductsController | `@PreAuthorize` isBuyerRole() | 200 | UNKNOWN | Returns every provider's products; providers cannot call this endpoint at all (see row 46 for the provider-scoped alternative). |
| 45 | GET | `/api/v1/fuel-products/{fuelProductId}` | FuelProductsController | manual: isBuyerRole() or ownsProvider(product.providerId) | 200, 404 | UNKNOWN | |
| 46 | GET | `/api/v1/fuel-products/provider/{providerId}` | FuelProductsController | `@PreAuthorize` isBuyerRole() or ownsProvider(providerId) | 200 | UNKNOWN | |
| 47 | PUT | `/api/v1/fuel-products/{fuelProductId}` | FuelProductsController | manual: ownsProvider(product.providerId) | 200, 404 | UNKNOWN | |
| 48 | DELETE | `/api/v1/fuel-products/{fuelProductId}` | FuelProductsController | manual: ownsProvider(product.providerId) | 204, 404 | UNKNOWN | **known-gap**: no check for open fuel-requests/orders referencing this product before hard-deleting it. |
| 49 | POST | `/api/v1/notifications` | NotificationsController | `@PreAuthorize` ownsUser(resource.userId) or ownsCompany(resource.companyId) or ownsProvider(resource.providerId) | 201, 400, 404 | UNKNOWN | Requires exactly one of userId/companyId/providerId; company/provider variants are resolved to a single target user via `UserRepository`. |
| 50 | POST | `/api/v1/notifications/{notificationId}/mark-as-read` | NotificationsController | manual: ownsUser(notification.userId) | 200, 404 | UNKNOWN | |
| 51 | GET | `/api/v1/notifications/{notificationId}` | NotificationsController | manual: ownsUser(notification.userId) | 200, 404 | UNKNOWN | |
| 52 | GET | `/api/v1/notifications/user/{userId}` | NotificationsController | `@PreAuthorize` ownsUser(userId) | 200 | UNKNOWN | |
| 53 | GET | `/api/v1/notifications/buyer/{companyId}` | NotificationsController | `@PreAuthorize` ownsCompany(companyId) | 200 | UNKNOWN | **known-gap**: internally calls `this.getNotificationsByUser(...)` as a plain Java method call, which bypasses that method's own `@PreAuthorize` (no proxy involved). Not currently exploitable because this endpoint already re-checks `ownsCompany` first, but the pattern is fragile if either method's authorization logic changes independently. |
| 54 | GET | `/api/v1/notifications/provider/{providerId}` | NotificationsController | `@PreAuthorize` ownsProvider(providerId) | 200 | UNKNOWN | Same internal-call gap as row 53. |
| 55 | GET | `/api/v1/notifications/user/{userId}/unread` | NotificationsController | `@PreAuthorize` ownsUser(userId) | 200 | UNKNOWN | |
| 56 | POST | `/api/v1/fuel-orders` | FuelOrdersController | `@PreAuthorize` ownsCompany(resource.companyId) | 201 | UNKNOWN | Direct order creation, independent of the fuel-request flow (rows 63–67). |
| 57 | POST | `/api/v1/fuel-orders/{orderId}/confirm` | FuelOrdersController | manual: ownsCompany(order.companyId) | 200, 404 | UNKNOWN | **known-gap**: `FuelOrder#confirm()` has no status guard (unlike `dispatch()`/`receive()`), so it can be called from any status including `CANCELLED`/`PAID`, and it moves the order into `CONFIRMED`, a status the delivery-dispatch guard (`PENDING` only) never expects — confirming an order before creating its delivery effectively strands it. Characterized in `OrderFulfillmentGoldenPathTest#confirmingAnOrderHasNoStateGuardAndCollidesWithTheDispatchFlow`. |
| 58 | POST | `/api/v1/fuel-orders/{orderId}/cancel` | FuelOrdersController | manual: ownsCompanyOrProvider(order.companyId, order.providerId) | 200, 404 | UNKNOWN | **known-gap**: `FuelOrder#cancel()` also has no status guard, and cancelling does not stop a payment from later being created against the same order (see row 68's note) — characterized in `OrderFulfillmentGoldenPathTest#cancellingAnOrderDoesNotBlockCreatingAPaymentForIt`. |
| 59 | GET | `/api/v1/fuel-orders` | FuelOrdersController | `@PreAuthorize` hasAuthority('ROLE_ADMIN') | 200 | UNKNOWN | |
| 60 | GET | `/api/v1/fuel-orders/{orderId}` | FuelOrdersController | manual: ownsCompanyOrProvider(order.companyId, order.providerId) | 200, 404 | UNKNOWN | |
| 61 | GET | `/api/v1/fuel-orders/company/{companyId}` | FuelOrdersController | `@PreAuthorize` ownsCompany(companyId) | 200 | UNKNOWN | |
| 62 | GET | `/api/v1/fuel-orders/provider/{providerId}` | FuelOrdersController | `@PreAuthorize` ownsProvider(providerId) | 200 | UNKNOWN | |
| 63 | POST | `/api/v1/fuel-requests` | FuelRequestsController | `@PreAuthorize` ownsCompany(resource.buyerCompanyId) | 201, 400 | UNKNOWN | |
| 64 | GET | `/api/v1/fuel-requests` | FuelRequestsController | manual: exactly one of `buyerCompanyId`/`providerId`, and caller must own it | 200, 400, 403 | UNKNOWN | **known-gap**: requires exactly one of the two query params — an admin (or a user with no ownership at all) cannot call this endpoint at all; both-present and both-absent are rejected identically as 400/403 depending on which branch is hit, and the 403 has no response body (`ResponseEntity.status(403).build()`), unlike every other denial path in this controller which returns 404. |
| 65 | GET | `/api/v1/fuel-requests/{requestId}` | FuelRequestsController | manual: ownsCompanyOrProvider(request.buyerCompanyId, request.providerId) | 200, 404 | UNKNOWN | |
| 66 | POST | `/api/v1/fuel-requests/{requestId}/accept` | FuelRequestsController | manual: ownsProvider(request.providerId) | 200, 404, 500 | UNKNOWN | **known-gap**: `FuelRequestService.accept()` throws a plain `IllegalStateException` ("Only pending requests can be accepted") when the request is not `PENDING`. `GlobalExceptionHandler` has no specific handler for `IllegalStateException`, so it falls through to the generic `RuntimeException` handler and comes back as `500 UNEXPECTED_ERROR` — not the `Result<T, ApplicationError>` pattern (with a proper 409) used by every command service elsewhere in the codebase. |
| 67 | POST | `/api/v1/fuel-requests/{requestId}/reject` | FuelRequestsController | manual: ownsProvider(request.providerId) | 200, 404, 400, 500 | UNKNOWN | Same `IllegalStateException` → 500 gap as row 66 for an already-processed request. A blank/missing `reason` throws `IllegalArgumentException`, which `GlobalExceptionHandler` does map to a clean 400. |
| 68 | POST | `/api/v1/payments` | PaymentsController | `@PreAuthorize` ownsCompany(resource.companyId) | 201, 400, 404 | UNKNOWN | **known-gap**: only checks that the order exists and that `amount` equals `order.totalPrice` — never checks `order.status`. A payment can be created against a `CANCELLED` order (see row 58's note); characterized in `OrderFulfillmentGoldenPathTest#cancellingAnOrderDoesNotBlockCreatingAPaymentForIt`. |
| 69 | POST | `/api/v1/payments/{paymentId}/complete` | PaymentsController | manual: ownsCompany(payment.companyId) or ownsProvider(order.providerId) | 200, 404 | UNKNOWN | Also calls `FuelOrder#markPaid()`, which only refuses a `CANCELLED` order — completing a payment created against a cancelled order (row 68) is itself blocked here, but the inconsistent order/payment pairing was already allowed to exist. |
| 70 | POST | `/api/v1/payments/{paymentId}/refund` | PaymentsController | manual: same as row 69 | 200, 404 | UNKNOWN | `Payment#refund()` has no status guard — a `PENDING` (never completed) payment can be "refunded". |
| 71 | GET | `/api/v1/payments` | PaymentsController | `@PreAuthorize` hasAuthority('ROLE_ADMIN') | 200 | UNKNOWN | |
| 72 | GET | `/api/v1/payments/{paymentId}` | PaymentsController | manual: ownsCompany(payment.companyId) or ownsProvider(order.providerId) | 200, 404 | UNKNOWN | |
| 73 | GET | `/api/v1/payments/order/{orderId}` | PaymentsController | manual: same as row 72 | 200, 404 | UNKNOWN | |
| 74 | GET | `/api/v1/payments/company/{companyId}` | PaymentsController | `@PreAuthorize` ownsCompany(companyId) | 200 | UNKNOWN | |
| 75 | GET | `/api/v1/analytics/platform` | AnalyticsController | `@PreAuthorize` hasAuthority('ROLE_ADMIN') | 200 | UNKNOWN | |
| 76 | GET | `/api/v1/analytics/providers/{providerId}` | AnalyticsController | `@PreAuthorize` ownsProvider(providerId) | 200 | UNKNOWN | |
| 77 | GET | `/api/v1/analytics/buyers/{companyId}` | AnalyticsController | `@PreAuthorize` ownsCompany(companyId) | 200 | UNKNOWN | |

## known-gap summary (not fixed in T01-A, characterization only)

1. **Row 1** — `GET /api/v1/provider-ratings` has no authorization scoping at all beyond "authenticated".
2. **Row 6, 43** — action-style `POST .../update` and `POST .../update-stock` routes instead of `PUT`/`PATCH`, inconsistent with the rest of the API.
3. **Row 10–13** — a `Delivery` is created already `DISPATCHED`; the `/dispatch` endpoint is a no-op in the only reachable flow; `dispatch()`/`fail()` have no status guards.
4. **Row 21, 22, 26, 27** — driver/vehicle update allows silent re-assignment across providers; delete has no in-flight-delivery check.
5. **Row 29** — sign-in returns 404 for unknown username vs. 400 for wrong password, leaking account existence and never returning a conventional 401.
6. **Row 32, 36** — buyer/provider company creation is public with no linked user account (tracked upstream by roadmap spec S04).
7. **Row 53, 54** — same-class method calls bypass `@PreAuthorize` on the callee (currently harmless because the caller re-checks, but fragile).
8. **Row 57, 58** — `FuelOrder#confirm()`/`cancel()` have no status guards and can strand or double-process an order.
9. **Row 64** — `GET /api/v1/fuel-requests` requires exactly one of two query params; no admin path, inconsistent 403 (no body) vs. 404 elsewhere.
10. **Row 66, 67** — fuel-request accept/reject on an already-processed request throws a raw `IllegalStateException`, which `GlobalExceptionHandler`'s generic `RuntimeException` fallback turns into `500 UNEXPECTED_ERROR` instead of a proper 409/400.
11. **Row 68, 70** — payment creation ignores order status; refund has no status guard.
