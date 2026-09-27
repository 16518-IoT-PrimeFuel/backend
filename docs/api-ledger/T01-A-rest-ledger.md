# T01-A — REST baseline ledger (S01)

Parent spec: S01 — Caracterizar contratos y estados actuales.
Alcance: ledger histórico y contratos vigentes. Las 77 rutas de la línea base se redujeron a 59
operaciones v1 activas tras los retiros T24-B; tras unificar v1/v2 sin prefijo de versión quedan 103 operaciones bajo `/api/**`. Los defectos observados se anotan como `known-gap`
según el estado actual del runtime.

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
The ledger now has 103 operations under `/api/**` after the v1/v2 unification (T24-C).

Auth column legend: `public` = matched by `permitAll()` in `WebSecurityConfiguration`;
`@PreAuthorize(expr)` = declared on the method; `manual: <check>` = no `@PreAuthorize`, the
controller performs the equivalent ownership check in code and returns 404/403/400 itself;
`authenticated` = no method-level restriction beyond the global `anyRequest().authenticated()`.

Consumer column: `UNKNOWN` unless there is direct evidence in this repo of a caller (there is no
sibling frontend/mobile repo checked out alongside this backend to inspect).

| # | Method | Path | Controller | Auth | Status codes | Consumer | Notes / known-gap |
|---|--------|------|------------|------|---------------|----------|--------------------|
| 1 | POST | `/api/equipment` | EquipmentController | `@PreAuthorize` ownsCompany(resource.companyId) | 201 | UNKNOWN | |
| 2 | POST | `/api/equipment/{equipmentId}/update` | EquipmentController | manual: ownsCompany(existing.companyId) | 200, 404 | UNKNOWN | **known-gap**: update is a `POST .../update` action route instead of `PUT /api/equipment/{id}`, inconsistent with the PUT-style updates used elsewhere (fuel-products, drivers, vehicles, buyer/provider companies). |
| 3 | GET | `/api/equipment` | EquipmentController | `@PreAuthorize` hasAuthority('ROLE_ADMIN') | 200 | UNKNOWN | |
| 4 | GET | `/api/equipment/{equipmentId}` | EquipmentController | manual: filter ownsCompany(equipment.companyId) | 200, 404 | UNKNOWN | |
| 5 | GET | `/api/equipment/company/{companyId}` | EquipmentController | `@PreAuthorize` ownsCompany(companyId) | 200 | UNKNOWN | |
| 6 | POST | `/api/authentication/sign-up` | AuthenticationController | public | 201, 400 | UNKNOWN | Exactly one of `buyerCompany`/`providerCompany` required; validated in `UserCommandServiceImpl`. |
| 7 | POST | `/api/authentication/sign-in` | AuthenticationController | public | 200, 400, 404 | UNKNOWN | **known-gap**: unknown username → 404 (`USER_NOT_FOUND`), wrong password → 400 (`VALIDATION_ERROR`). Different codes for the two failure modes leak whether a username is registered, and neither is the conventional 401. |
| 8 | POST | `/api/authentication/password-reset/request` | AuthenticationController | public | 202 | UNKNOWN | Always 202 regardless of whether the account exists — deliberately non-revealing, unlike sign-in (row 29). |
| 9 | POST | `/api/authentication/password-reset/confirm` | AuthenticationController | public | 204, 400 | UNKNOWN | Token is single-use (400 on reuse), validated by `FullTankPlatformApplicationTests#passwordResetIsDeliveredOnceAndChangesThePassword`. |
| 10 | POST | `/api/buyer-companies` | BuyerCompaniesController | public (`permitAll` on this exact path) | 201 | UNKNOWN | **known-gap** (tracked by roadmap spec S04): creates a buyer company shell with no associated user account; anyone unauthenticated can call this. |
| 11 | GET | `/api/buyer-companies` | BuyerCompaniesController | `@PreAuthorize` hasAuthority('ROLE_ADMIN') | 200 | UNKNOWN | |
| 12 | GET | `/api/buyer-companies/{companyId}` | BuyerCompaniesController | `@PreAuthorize` ownsCompany(companyId) | 200, 404 | UNKNOWN | |
| 13 | PUT | `/api/buyer-companies/{companyId}` | BuyerCompaniesController | `@PreAuthorize` ownsCompany(companyId) | 200, 404 | UNKNOWN | |
| 14 | POST | `/api/provider-companies` | ProviderCompaniesController | public (`permitAll` on this exact path) | 201 | UNKNOWN | Same known-gap as row 32, mirrored for providers. |
| 15 | GET | `/api/provider-companies` | ProviderCompaniesController | `@PreAuthorize` isBuyerRole() | 200 | UNKNOWN | Providers cannot list other providers; only buyers (browsing the marketplace) and admins-as-buyers can. |
| 16 | GET | `/api/provider-companies/{providerId}` | ProviderCompaniesController | `@PreAuthorize` isBuyerRole() or ownsProvider(providerId) | 200, 404 | UNKNOWN | |
| 17 | PUT | `/api/provider-companies/{providerId}` | ProviderCompaniesController | `@PreAuthorize` ownsProvider(providerId) | 200, 404 | UNKNOWN | |
| 18 | GET | `/api/users` | UsersController | `@PreAuthorize` hasAuthority('ROLE_ADMIN') | 200 | UNKNOWN | |
| 19 | GET | `/api/users/{userId}` | UsersController | `@PreAuthorize` ownsUser(userId) | 200, 404 | UNKNOWN | |
| 20 | POST | `/api/fuel-products` | FuelProductsController | `@PreAuthorize` ownsProvider(resource.providerId) | 201 | UNKNOWN | |
| 21 | POST | `/api/fuel-products/{fuelProductId}/update-stock` | FuelProductsController | manual: ownsProvider(product.providerId) | 200, 404 | UNKNOWN | **known-gap**: another `POST .../action` route instead of a dedicated `PATCH`/`PUT`, same inconsistency as row 6. |
| 22 | GET | `/api/fuel-products` | FuelProductsController | `@PreAuthorize` isBuyerRole() | 200 | UNKNOWN | Returns every provider's products; providers cannot call this endpoint at all (see row 46 for the provider-scoped alternative). |
| 23 | GET | `/api/fuel-products/{fuelProductId}` | FuelProductsController | manual: isBuyerRole() or ownsProvider(product.providerId) | 200, 404 | UNKNOWN | |
| 24 | GET | `/api/fuel-products/provider/{providerId}` | FuelProductsController | `@PreAuthorize` isBuyerRole() or ownsProvider(providerId) | 200 | UNKNOWN | |
| 25 | PUT | `/api/fuel-products/{fuelProductId}` | FuelProductsController | manual: ownsProvider(product.providerId) | 200, 404 | UNKNOWN | |
| 26 | DELETE | `/api/fuel-products/{fuelProductId}` | FuelProductsController | manual: ownsProvider(product.providerId) | 204, 404 | UNKNOWN | **known-gap**: no check for open fuel-requests/orders referencing this product before hard-deleting it. |
| 27 | POST | `/api/fuel-orders` | FuelOrdersController | `@PreAuthorize` ownsCompany(resource.companyId) | 201 | UNKNOWN | Direct order creation, independent of the fuel-request flow (rows 63–67). |
| 28 | POST | `/api/fuel-orders/{orderId}/confirm` | FuelOrdersController | manual: ownsCompany(order.companyId) | 200, 404 | UNKNOWN | **known-gap**: `FuelOrder#confirm()` has no status guard (unlike `dispatch()`/`receive()`), so it can be called from any status including `CANCELLED`/`PAID`, and it moves the order into `CONFIRMED`, a status the delivery-dispatch guard (`PENDING` only) never expects — confirming an order before creating its delivery effectively strands it. Characterized in `OrderFulfillmentGoldenPathTest#confirmingAnOrderHasNoStateGuardAndCollidesWithTheDispatchFlow`. |
| 29 | POST | `/api/fuel-orders/{orderId}/cancel` | FuelOrdersController | manual: ownsCompanyOrProvider(order.companyId, order.providerId) | 200, 404 | UNKNOWN | **known-gap**: `FuelOrder#cancel()` also has no status guard, and cancelling does not stop a payment from later being created against the same order (see row 68's note) — characterized in `OrderFulfillmentGoldenPathTest#cancellingAnOrderDoesNotBlockCreatingAPaymentForIt`. |
| 30 | GET | `/api/fuel-orders` | FuelOrdersController | `@PreAuthorize` hasAuthority('ROLE_ADMIN') | 200 | UNKNOWN | |
| 31 | GET | `/api/fuel-orders/{orderId}` | FuelOrdersController | manual: ownsCompanyOrProvider(order.companyId, order.providerId) | 200, 404 | UNKNOWN | |
| 32 | GET | `/api/fuel-orders/company/{companyId}` | FuelOrdersController | `@PreAuthorize` ownsCompany(companyId) | 200 | UNKNOWN | |
| 33 | GET | `/api/fuel-orders/provider/{providerId}` | FuelOrdersController | `@PreAuthorize` ownsProvider(providerId) | 200 | UNKNOWN | |
| 34 | POST | `/api/payments` | PaymentsController | `@PreAuthorize` ownsCompany(resource.companyId) | 201, 400, 404 | UNKNOWN | **known-gap**: only checks that the order exists and that `amount` equals `order.totalPrice` — never checks `order.status`. A payment can be created against a `CANCELLED` order (see row 58's note); characterized in `OrderFulfillmentGoldenPathTest#cancellingAnOrderDoesNotBlockCreatingAPaymentForIt`. |
| 35 | POST | `/api/payments/{paymentId}/complete` | PaymentsController | manual: ownsCompany(payment.companyId) or ownsProvider(order.providerId) | 200, 404 | UNKNOWN | Also calls `FuelOrder#markPaid()`, which only refuses a `CANCELLED` order — completing a payment created against a cancelled order (row 68) is itself blocked here, but the inconsistent order/payment pairing was already allowed to exist. |
| 36 | POST | `/api/payments/{paymentId}/refund` | PaymentsController | manual: same as row 69 | 200, 404 | UNKNOWN | `Payment#refund()` has no status guard — a `PENDING` (never completed) payment can be "refunded". |
| 37 | GET | `/api/payments` | PaymentsController | `@PreAuthorize` hasAuthority('ROLE_ADMIN') | 200 | UNKNOWN | |
| 38 | GET | `/api/payments/{paymentId}` | PaymentsController | manual: ownsCompany(payment.companyId) or ownsProvider(order.providerId) | 200, 404 | UNKNOWN | |
| 39 | GET | `/api/payments/order/{orderId}` | PaymentsController | manual: same as row 72 | 200, 404 | UNKNOWN | |
| 40 | GET | `/api/payments/company/{companyId}` | PaymentsController | `@PreAuthorize` ownsCompany(companyId) | 200 | UNKNOWN | |
| 41 | GET | `/api/analytics/platform` | AnalyticsController | `@PreAuthorize` hasAuthority('ROLE_ADMIN') | 200 | UNKNOWN | |
| 42 | GET | `/api/analytics/providers/{providerId}` | AnalyticsController | `@PreAuthorize` ownsProvider(providerId) | 200 | UNKNOWN | |
| 43 | GET | `/api/analytics/buyers/{companyId}` | AnalyticsController | `@PreAuthorize` ownsCompany(companyId) | 200 | UNKNOWN | |
| 44 | GET | `/api/tanks/{tankId}/refill-policy` | Políticas de reposición | ver Swagger | 200, 403, 404 | UNKNOWN | Ex-v2; ruta sin versión desde la unificación. |
| 45 | PUT | `/api/tanks/{tankId}/refill-policy` | Políticas de reposición | ver Swagger | 200, 400, 403 | UNKNOWN | Ex-v2; ruta sin versión desde la unificación. |
| 46 | GET | `/api/tankers/{tankerId}` | Cisternas de flota | ver Swagger | 200, 403, 404 | UNKNOWN | Ex-v2; ruta sin versión desde la unificación. |
| 47 | PUT | `/api/tankers/{tankerId}` | Cisternas de flota | ver Swagger | 200, 400, 404 | UNKNOWN | Ex-v2; ruta sin versión desde la unificación. |
| 48 | GET | `/api/drivers/{driverId}` | Conductores de flota | ver Swagger | 200, 403, 404 | UNKNOWN | Ex-v2; ruta sin versión desde la unificación. |
| 49 | PUT | `/api/drivers/{driverId}` | Conductores de flota | ver Swagger | 200, 400, 404 | UNKNOWN | Ex-v2; ruta sin versión desde la unificación. |
| 50 | POST | `/api/telemetry/readings` | Telemetría | public (X-Device-Token) | 202, 400 | UNKNOWN | Ex-v2; ruta sin versión desde la unificación. |
| 51 | GET | `/api/tanks` | Cisternas | ver Swagger | 200, 403 | UNKNOWN | Ex-v2; ruta sin versión desde la unificación. |
| 52 | POST | `/api/tanks` | Cisternas | ver Swagger | 201, 400, 403, 409 | UNKNOWN | Ex-v2; ruta sin versión desde la unificación. |
| 53 | GET | `/api/tankers` | Cisternas de flota | ver Swagger | 200, 403 | UNKNOWN | Ex-v2; ruta sin versión desde la unificación. |
| 54 | POST | `/api/tankers` | Cisternas de flota | ver Swagger | 201, 400, 403 | UNKNOWN | Ex-v2; ruta sin versión desde la unificación. |
| 55 | POST | `/api/tankers/{tankerId}/deactivate` | Cisternas de flota | ver Swagger | 200, 404 | UNKNOWN | Ex-v2; ruta sin versión desde la unificación. |
| 56 | POST | `/api/tankers/{tankerId}/activate` | Cisternas de flota | ver Swagger | 200, 404 | UNKNOWN | Ex-v2; ruta sin versión desde la unificación. |
| 57 | GET | `/api/replenishment-requests` | Solicitudes de abastecimiento | ver Swagger | 200, 403 | UNKNOWN | Ex-v2; ruta sin versión desde la unificación. |
| 58 | POST | `/api/replenishment-requests` | Solicitudes de abastecimiento | ver Swagger | 201, 400, 403, 404 | UNKNOWN | Ex-v2; ruta sin versión desde la unificación. |
| 59 | POST | `/api/replenishment-requests/{requestId}/reject` | Solicitudes de abastecimiento | ver Swagger | 200, 400, 403, 404, 409 | UNKNOWN | Ex-v2; ruta sin versión desde la unificación. |
| 60 | POST | `/api/replenishment-requests/{requestId}/cancel` | Solicitudes de abastecimiento | ver Swagger | 200, 403, 404, 409 | UNKNOWN | Ex-v2; ruta sin versión desde la unificación. |
| 61 | POST | `/api/replenishment-requests/{requestId}/accept` | Solicitudes de abastecimiento | ver Swagger | 200, 403, 404, 409 | UNKNOWN | Ex-v2; ruta sin versión desde la unificación. |
| 62 | POST | `/api/organizations/{organizationId}/invitations` | Invitaciones | ver Swagger | 201, 400, 403, 404, 409 | UNKNOWN | Ex-v2; ruta sin versión desde la unificación. |
| 63 | POST | `/api/onboarding` | Registro de organizaciones | ver Swagger | 201, 400, 403, 409 | UNKNOWN | Ex-v2; ruta sin versión desde la unificación. |
| 64 | POST | `/api/me/notifications/{notificationId}/read` | Mis notificaciones | ver Swagger | 200, 403, 404 | UNKNOWN | Ex-v2; ruta sin versión desde la unificación. |
| 65 | POST | `/api/invitations/{token}/accept` | Invitaciones | ver Swagger | 200, 403, 404, 409, 422 | UNKNOWN | Ex-v2; ruta sin versión desde la unificación. |
| 66 | GET | `/api/drivers` | Conductores de flota | ver Swagger | 200, 403 | UNKNOWN | Ex-v2; ruta sin versión desde la unificación. |
| 67 | POST | `/api/drivers` | Conductores de flota | ver Swagger | 201, 400, 403 | UNKNOWN | Ex-v2; ruta sin versión desde la unificación. |
| 68 | POST | `/api/drivers/{driverId}/deactivate` | Conductores de flota | ver Swagger | 200, 404 | UNKNOWN | Ex-v2; ruta sin versión desde la unificación. |
| 69 | POST | `/api/drivers/{driverId}/activate` | Conductores de flota | ver Swagger | 200, 404 | UNKNOWN | Ex-v2; ruta sin versión desde la unificación. |
| 70 | POST | `/api/deliveries` | Asignación de entregas | ver Swagger | 201, 400, 403, 404, 409 | UNKNOWN | Ex-v2; ruta sin versión desde la unificación. |
| 71 | POST | `/api/deliveries/{deliveryId}/valve-observations` | Observaciones de válvula | ver Swagger | 200, 202, 400, 403, 404 | UNKNOWN | Ex-v2; ruta sin versión desde la unificación. |
| 72 | POST | `/api/deliveries/{deliveryId}/transport-evidence` | Evidencias de transporte | ver Swagger | 200, 201, 400, 403, 404, 422 | UNKNOWN | Ex-v2; ruta sin versión desde la unificación. |
| 73 | POST | `/api/deliveries/{deliveryId}/start` | Ciclo de entrega | ver Swagger | 200, 404, 409 | UNKNOWN | Ex-v2; ruta sin versión desde la unificación. |
| 74 | POST | `/api/deliveries/{deliveryId}/geofence-policies` | Políticas de geocerca | ver Swagger | 201, 400, 403, 404, 409 | UNKNOWN | Ex-v2; ruta sin versión desde la unificación. |
| 75 | POST | `/api/deliveries/{deliveryId}/fail` | Ciclo de entrega | ver Swagger | 200, 404, 409 | UNKNOWN | Ex-v2; ruta sin versión desde la unificación. |
| 76 | POST | `/api/deliveries/{deliveryId}/complete` | Ciclo de entrega | ver Swagger | 200, 400, 404, 409, 422 | UNKNOWN | Ex-v2; ruta sin versión desde la unificación. |
| 77 | POST | `/api/deliveries/{deliveryId}/cancel` | Ciclo de entrega | ver Swagger | 200, 404, 409 | UNKNOWN | Ex-v2; ruta sin versión desde la unificación. |
| 78 | POST | `/api/deliveries/{deliveryId}/assign` | Ciclo de entrega | ver Swagger | 200, 404, 409 | UNKNOWN | Ex-v2; ruta sin versión desde la unificación. |
| 79 | POST | `/api/deliveries/{deliveryId}/arrive` | Ciclo de entrega | ver Swagger | 200, 404, 409 | UNKNOWN | Ex-v2; ruta sin versión desde la unificación. |
| 80 | GET | `/api/customers` | Clientes | ver Swagger | 200, 403 | UNKNOWN | Ex-v2; ruta sin versión desde la unificación. |
| 81 | POST | `/api/customers` | Clientes | ver Swagger | 201, 400, 403, 409 | UNKNOWN | Ex-v2; ruta sin versión desde la unificación. |
| 82 | GET | `/api/customers/{customerId}/sites` | Clientes | ver Swagger | 200, 403, 404 | UNKNOWN | Ex-v2; ruta sin versión desde la unificación. |
| 83 | POST | `/api/customers/{customerId}/sites` | Clientes | ver Swagger | 201, 400, 403, 404 | UNKNOWN | Ex-v2; ruta sin versión desde la unificación. |
| 84 | POST | `/api/admin/users/{userId}/promote` | Administración de plataforma | ver Swagger | 200, 403, 404 | UNKNOWN | Ex-v2; ruta sin versión desde la unificación. |
| 85 | GET | `/api/tanks/{tankId}` | Cisternas | ver Swagger | 200, 403, 404 | UNKNOWN | Ex-v2; ruta sin versión desde la unificación. |
| 86 | GET | `/api/tanks/{tankId}/refill-episodes` | Políticas de reposición | ver Swagger | 200, 403 | UNKNOWN | Ex-v2; ruta sin versión desde la unificación. |
| 87 | GET | `/api/tankers/{tankerId}/eligibility` | Cisternas de flota | ver Swagger | 200, 403, 404 | UNKNOWN | Ex-v2; ruta sin versión desde la unificación. |
| 88 | GET | `/api/tankers/eligible` | Cisternas de flota | ver Swagger | 200, 403 | UNKNOWN | Ex-v2; ruta sin versión desde la unificación. |
| 89 | GET | `/api/replenishment-requests/{requestId}` | Solicitudes de abastecimiento | ver Swagger | 200, 403, 404 | UNKNOWN | Ex-v2; ruta sin versión desde la unificación. |
| 90 | GET | `/api/me/organizations` | Mis organizaciones | ver Swagger | 200, 403 | UNKNOWN | Ex-v2; ruta sin versión desde la unificación. |
| 91 | GET | `/api/me/notifications` | Mis notificaciones | ver Swagger | 200, 403 | UNKNOWN | Ex-v2; ruta sin versión desde la unificación. |
| 92 | GET | `/api/me/notifications/unread` | Mis notificaciones | ver Swagger | 200, 403 | UNKNOWN | Ex-v2; ruta sin versión desde la unificación. |
| 93 | GET | `/api/drivers/{driverId}/eligibility` | Conductores de flota | ver Swagger | 200, 403, 404 | UNKNOWN | Ex-v2; ruta sin versión desde la unificación. |
| 94 | GET | `/api/drivers/eligible` | Conductores de flota | ver Swagger | 200, 403 | UNKNOWN | Ex-v2; ruta sin versión desde la unificación. |
| 95 | GET | `/api/deliveries/{deliveryId}` | Ciclo de entrega | ver Swagger | 200, 404 | UNKNOWN | Ex-v2; ruta sin versión desde la unificación. |
| 96 | GET | `/api/deliveries/{deliveryId}/transitions` | Ciclo de entrega | ver Swagger | 200, 404 | UNKNOWN | Ex-v2; ruta sin versión desde la unificación. |
| 97 | GET | `/api/deliveries/{deliveryId}/tracking` | Seguimiento de transporte | ver Swagger | 200, 403, 404 | UNKNOWN | Ex-v2; ruta sin versión desde la unificación. |
| 98 | GET | `/api/deliveries/{deliveryId}/tracking/samples` | Seguimiento de transporte | ver Swagger | 200, 403, 404 | UNKNOWN | Ex-v2; ruta sin versión desde la unificación. |
| 99 | GET | `/api/deliveries/{deliveryId}/timeline` | Cronología de entregas | ver Swagger | 200, 403, 404 | UNKNOWN | Ex-v2; ruta sin versión desde la unificación. |
| 100 | GET | `/api/admin/deliveries/{deliveryId}/transport-evidence/export` | Retención de evidencias de transporte | ver Swagger | 200, 403, 404 | UNKNOWN | Ex-v2; ruta sin versión desde la unificación. |
| 101 | GET | `/api/admin/api-metrics` | Métricas de rutas API | ver Swagger | 200, 400, 403 | UNKNOWN | Ex-v2; ruta sin versión desde la unificación. |
| 102 | DELETE | `/api/invitations/{invitationId}` | Invitaciones | ver Swagger | 200, 403, 404, 409 | UNKNOWN | Ex-v2; ruta sin versión desde la unificación. |
| 103 | DELETE | `/api/admin/deliveries/{deliveryId}/transport-evidence` | Retención de evidencias de transporte | ver Swagger | 204, 403, 404 | UNKNOWN | Ex-v2; ruta sin versión desde la unificación. |

## Resumen de brechas conocidas (no corregidas en T01-A)

1. Action-style `POST .../update` and `POST .../update-stock` routes remain in equipment and inventory.
2. The legacy delivery lifecycle can create a delivery as `DISPATCHED`; `/dispatch` can be a no-op and `dispatch()`/`fail()` lack status guards.
3. Sign-in distinguishes unknown usernames (404) from wrong passwords (400).
4. Buyer/provider company creation remains public without a linked user account (tracked by S04).
5. Legacy fuel-request list/decision behavior still has authorization and error-mapping gaps; see the three live fuel-request rows above. The family remains until the v2 acceptance flow can create and attach its order atomically.
6. Fuel-order confirm/cancel and payment refund lack status guards; payment creation does not check order status.

Decision: the v1 driver, vehicle, and provider-rating operations were retired by product decision on 2026-09-26. Driver and tanker lifecycle continues through the v2 fleet controllers; provider ratings have no replacement.
