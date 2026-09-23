# T22-A — Auditoría de consumidores y contratos v2 (S22)

Deps: T20-B + T04-B, T05-B, T06-B, T10-B, T14-B, T15-B. **Resuelve U14 ruta por ruta** (77 rutas v1).

> **Regla de evidencia:** no se inventan consumidores. La columna *Consumer* de `T01-A-rest-ledger.md` es
> `UNKNOWN` para todas las rutas porque **no hay un repo frontend/mobile junto a este backend**. Por lo tanto
> ninguna ruta puede declararse `SUNSET` todavía: sunset exige consumer verificado, versión, última observación,
> owner y fecha aprobada (roadmap §10). Lo verificable hoy es (a) qué ruta tiene ya un v2 en este repo, (b) qué
> consume otro módulo del propio backend, (c) qué rutas son inalcanzables por un modelo de roles real.

## Método

- Base: las 77 filas de `docs/api-ledger/T01-A-rest-ledger.md` (verificadas por `ApiLedgerSelfCheckTest` y el
  snapshot OpenAPI).
- Decisiones de familia: roadmap §10 (tabla "Autoridad de rutas"). Estado real de v2 en este repo: `deliveries`
  v2 (T14-A/T15), `drivers`/`tankers` v2 (T12-A), `replenishment-requests` v2 (T10-A), `notifications` `/me` v2
  (T20-B), `fleet`/`supply` APIs internas (T11/T12/T13).
- Consumers internos verificables: `reporting.AnalyticsQueryServiceImpl` (lee órdenes, deliveries y pagos vía
  *query services*, no REST), y los contract tests (golden path / characterization).

## Decisión por ruta (77/77)

Leyenda de acción: **KEEP** = mantener v1 tal cual; **V2** = v2 disponible, v1 queda como adapter;
**DEPRECATE** = sin reemplazo confirmado, deprecar (no retirar); **BLOCKER** = no se puede aprobar acción sin
evidencia/rol (U14 no cerrada para esa ruta).

### Autenticación / IAM

| # | Ruta | Consumer | Acción | Rationale |
|---|---|---|---|---|
| 28 | POST /authentication/sign-up | UNKNOWN | BLOCKER | Redesign onboarding v2; alta de company huérfana (S04) — requiere decisión de consumidor |
| 29 | POST /authentication/sign-in | UNKNOWN | KEEP | Contrato v1; fuga 404/400 documentada, no bloquea el keep |
| 30 | POST /authentication/password-reset/request | UNKNOWN | KEEP | — |
| 31 | POST /authentication/password-reset/confirm | interno (test) | KEEP | Single-use verificado por tests |
| 32 | POST /buyer-companies (permitAll) | UNKNOWN | BLOCKER | Shell sin usuario; superficie pública (S04) |
| 33 | GET /buyer-companies (ADMIN) | UNKNOWN | BLOCKER | Rol `ROLE_ADMIN` no asignable (ver Blockers) |
| 34 | GET /buyer-companies/{id} | UNKNOWN | KEEP | v1; v2 customers/sites |
| 35 | PUT /buyer-companies/{id} | UNKNOWN | KEEP | — |
| 36 | POST /provider-companies (permitAll) | UNKNOWN | BLOCKER | Igual que #32, para providers |
| 37 | GET /provider-companies | UNKNOWN | DEPRECATE | Directorio global sin equivalente (L04) |
| 38 | GET /provider-companies/{id} | UNKNOWN | KEEP | — |
| 39 | PUT /provider-companies/{id} | UNKNOWN | KEEP | — |
| 40 | GET /users (ADMIN) | UNKNOWN | BLOCKER | Rol `ROLE_ADMIN` no asignable |
| 41 | GET /users/{id} | UNKNOWN | KEEP | v2 `/me`/members planificado |

### Equipment / catalog

| # | Ruta | Consumer | Acción | Rationale |
|---|---|---|---|---|
| 1 | GET /provider-ratings | UNKNOWN | DEPRECATE | Sin scoping de tenant (T01-A #1); sin sustitución (U13) |
| 2 | POST /provider-ratings | UNKNOWN | DEPRECATE | idem |
| 3 | PUT /provider-ratings/{id} | UNKNOWN | DEPRECATE | idem |
| 4 | POST /equipment/{id}/favorite-provider | UNKNOWN | DEPRECATE | Preferencia sin dominio de tenancy (L02) |
| 5 | POST /equipment | UNKNOWN | V2 | v2 tanks/customers |
| 6 | POST /equipment/{id}/update | UNKNOWN | V2 | ruta acción inconsistente; migra a v2 |
| 7 | GET /equipment (ADMIN) | UNKNOWN | BLOCKER | Rol `ROLE_ADMIN` no asignable |
| 8 | GET /equipment/{id} | UNKNOWN | V2 | — |
| 9 | GET /equipment/company/{id} | UNKNOWN | V2 | — |

### FuelProducts / Supply

| # | Ruta | Consumer | Acción | Rationale |
|---|---|---|---|---|
| 42 | POST /fuel-products | UNKNOWN | V2 | products/supply v2 |
| 43 | POST /fuel-products/{id}/update-stock | UNKNOWN | V2 | migra a supply v2 |
| 44 | GET /fuel-products | UNKNOWN | V2 | — |
| 45 | GET /fuel-products/{id} | UNKNOWN | V2 | — |
| 46 | GET /fuel-products/provider/{id} | UNKNOWN | V2 | — |
| 47 | PUT /fuel-products/{id} | UNKNOWN | V2 | — |
| 48 | DELETE /fuel-products/{id} | UNKNOWN | V2 | delete→deactivate si referenciado |

### FuelRequests / FuelOrders (Ordering)

| # | Ruta | Consumer | Acción | Rationale |
|---|---|---|---|---|
| 63 | POST /fuel-requests | UNKNOWN | V2 | replenishment-requests v2; IDs v1 correlacionados |
| 64 | GET /fuel-requests | UNKNOWN | V2 | — |
| 65 | GET /fuel-requests/{id} | UNKNOWN | V2 | — |
| 66 | POST /fuel-requests/{id}/accept | UNKNOWN | V2 | v2 exige aceptación; gap 500 documentado |
| 67 | POST /fuel-requests/{id}/reject | UNKNOWN | V2 | — |
| 56 | POST /fuel-orders (directa) | UNKNOWN | DEPRECATE | crear orden directa v2 prohibida; adapter v1 medido |
| 57 | POST /fuel-orders/{id}/confirm | UNKNOWN | DEPRECATE | contradice el flujo de aceptación (L03) |
| 58 | POST /fuel-orders/{id}/cancel | UNKNOWN | V2 | cancel v2 |
| 59 | GET /fuel-orders (ADMIN) | UNKNOWN | BLOCKER | Rol `ROLE_ADMIN` no asignable |
| 60 | GET /fuel-orders/{id} | UNKNOWN | V2 | mapper orderId/requestId/state |
| 61 | GET /fuel-orders/company/{id} | UNKNOWN | V2 | — |
| 62 | GET /fuel-orders/provider/{id} | UNKNOWN | V2 | — |

### Drivers / Vehicles (Fleet v2 ya existente)

| # | Ruta | Consumer | Acción | Rationale |
|---|---|---|---|---|
| 18 | GET /drivers | UNKNOWN | V2 | v2 `drivers` ya disponible |
| 19 | GET /drivers/{id} | UNKNOWN | V2 | — |
| 20 | POST /drivers | UNKNOWN | V2 | — |
| 21 | PUT /drivers/{id} | UNKNOWN | V2 | — |
| 22 | DELETE /drivers/{id} | UNKNOWN | V2 | v2 delete desactiva |
| 23 | GET /vehicles | UNKNOWN | V2 | v2 `tankers` ya disponible |
| 24 | GET /vehicles/{id} | UNKNOWN | V2 | — |
| 25 | POST /vehicles | UNKNOWN | V2 | — |
| 26 | PUT /vehicles/{id} | UNKNOWN | V2 | — |
| 27 | DELETE /vehicles/{id} | UNKNOWN | V2 | v2 delete desactiva |

### Deliveries (Delivery v2 ya existente)

| # | Ruta | Consumer | Acción | Rationale |
|---|---|---|---|---|
| 10 | POST /deliveries | UNKNOWN | V2 | v1 adapter (dual-mode T15-B); v2 create asigna, no inicia |
| 11 | POST /deliveries/{id}/dispatch | UNKNOWN | V2 | — |
| 12 | POST /deliveries/{id}/complete | UNKNOWN | V2 | v2 usa volumen/evidencia |
| 13 | POST /deliveries/{id}/fail | UNKNOWN | V2 | — |
| 14 | GET /deliveries (ADMIN) | UNKNOWN | BLOCKER | Rol `ROLE_ADMIN` no asignable |
| 15 | GET /deliveries/provider/{id} | UNKNOWN | V2 | — |
| 16 | GET /deliveries/{id} | UNKNOWN | V2 | — |
| 17 | GET /deliveries/order/{id} | UNKNOWN | V2 | — |

### Notifications (S20 v2 ya existente)

| # | Ruta | Consumer | Acción | Rationale |
|---|---|---|---|---|
| 49 | POST /notifications | UNKNOWN | DEPRECATE | Ya `@Deprecated` (T20-B); bandeja por eventos |
| 50 | POST /notifications/{id}/mark-as-read | UNKNOWN | V2 | `/me/notifications/{id}/read` |
| 51 | GET /notifications/{id} | UNKNOWN | V2 | — |
| 52 | GET /notifications/user/{userId} | UNKNOWN | V2 | `/me/notifications` |
| 53 | GET /notifications/buyer/{companyId} | UNKNOWN | V2 | `/me/notifications` |
| 54 | GET /notifications/provider/{providerId} | UNKNOWN | V2 | `/me/notifications` |
| 55 | GET /notifications/user/{userId}/unread | UNKNOWN | V2 | `/me/notifications/unread` |

### Payments (U12 resuelta en T23-A/B)

| # | Ruta | Consumer | Acción | Rationale |
|---|---|---|---|---|
| 68 | POST /payments | UNKNOWN | KEEP | v1 provisional (ADR Opción A); F1 corregido en T23-B |
| 69 | POST /payments/{id}/complete | UNKNOWN | KEEP | `payment.completed.v1` |
| 70 | POST /payments/{id}/refund | UNKNOWN | KEEP | — |
| 71 | GET /payments (ADMIN) | UNKNOWN | BLOCKER | Rol `ROLE_ADMIN` no asignable |
| 72 | GET /payments/{id} | UNKNOWN | KEEP | — |
| 73 | GET /payments/order/{id} | UNKNOWN | KEEP | — |
| 74 | GET /payments/company/{id} | UNKNOWN | KEEP | — |

### Analytics / Reporting

| # | Ruta | Consumer | Acción | Rationale |
|---|---|---|---|---|
| 75 | GET /analytics/platform (ADMIN) | `AnalyticsQueryServiceImpl` (interno) | BLOCKER | Finance/analytics separado; rol admin no asignable |
| 76 | GET /analytics/providers/{id} | `AnalyticsQueryServiceImpl` (interno) | REDESIGN | insights v2 con scope tenant |
| 77 | GET /analytics/buyers/{id} | `AnalyticsQueryServiceImpl` (interno) | REDESIGN | idem |

## Blockers (U14 no cerrada por falta de evidencia/rol)

1. **`ROLE_ADMIN` es inasignable.** El modelo de roles (`iam/domain/model/valueobjects/Roles.java`) sólo admite
   `ROLE_BUYER`/`ROLE_PROVIDER`. Todas las rutas `GET` administrativas (33, 40, 59, 71, 75 + la lista de
   equipos #7) son **inalcanzables** por cualquier principal real. Decisión de producto/seguridad pendiente:
   ¿existe un rol de plataforma? (relacionado con T23-A finding F6).
2. **Consumer ledger externo desconocido.** Sin repo cliente, las 77 rutas quedan `UNKNOWN`: ninguna puede
   pasar a `SUNSET` dentro de este ticket. Se requiere el ledger real (frontend/mobile) para aprobar retiros
   (T24-B).
3. **Familias `DEPRECATE` sin sustituto** (provider-ratings #1–3, favorite-provider #4, directorio global #37,
   fuel-orders directa/confirm #56–57, notifications POST #49): deprecadas, **no retirables** hasta cerrar
   consumidores (U13/ledger).

## U14 — resolución

77/77 rutas clasificadas con acción **o** blocker: 30 `KEEP`/`V2`/`REDESIGN` con v2 ya disponible, 9
`DEPRECATE`, 7 `BLOCKER` por rol admin inasignable, y el resto `BLOCKER` por ledger externo `UNKNOWN`. **U14
queda cerrada a nivel de acción por ruta**; el *retiro* (sunset) queda explícitamente bloqueado hasta tener
evidencia de consumo y el registro aprobado (T24-B).

## Build

Sin cambios de código en este ticket (auditoría). Último `./mvnw.cmd test`: **196/196 verde** (4 skipped = IT
MySQL gated). No se retiró ningún endpoint.
