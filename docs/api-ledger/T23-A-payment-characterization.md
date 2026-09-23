# T23-A — Caracterización y decisión de Payment (S23)

Deps: T01-B, T02-B, T04-B, T14-B. **Ticket de caracterización + ADR, sin refactor.** No se borró ni renombró
nada, y no se cambió el comportamiento de las 7 rutas v1 (verificado: `git status` no muestra cambios bajo
`src/main/java/com/primefuel/fulltank/platform/payment/`). El desacoplamiento es T23-B.

> Evidencia: `payment/application/internal/commandservices/PaymentCommandServiceImpl.java`,
> `payment/interfaces/rest/PaymentsController.java`, `payment/domain/model/aggregates/Payment.java`,
> `payment/domain/model/valueobjects/{PaymentStatus,PaymentMethod}.java`,
> `payment/infrastructure/persistence/jpa/entities/PaymentPersistenceEntity.java`,
> `reporting/application/internal/queryservices/AnalyticsQueryServiceImpl.java`,
> `db/migration/V1__baseline.sql`, y los tests de `PaymentCharacterizationTest`.

## 1. Comportamiento actual (7 rutas /api/v1/payments)

| # | Método | Ruta | Autorización | Códigos reales | Notas |
|---|---|---|---|---|---|
| 1 | POST | `/payments` | `@PreAuthorize ownsCompany(#resource.companyId())` | 201, 400, 403, 404, 409 | Valida order+company y `amount == order.totalPrice` en el **controller**; **no** consulta `order.status` |
| 2 | POST | `/{id}/complete` | manual: ownsCompany(payment.companyId) **o** ownsProvider(order.providerId) | 200, 404, **500** | Marca Payment `COMPLETED` y FuelOrder `PAID` |
| 3 | POST | `/{id}/refund` | manual: igual que #2 | 200, 404 | Marca `REFUNDED`; **no** revierte la orden |
| 4 | GET | `/payments` | `@PreAuthorize hasAuthority('ROLE_ADMIN')` | **403 siempre** (ver F6) | |
| 5 | GET | `/{id}` | manual: ownsCompany(payment.companyId) o ownsProvider(order.providerId) | 200, 404 | |
| 6 | GET | `/order/{orderId}` | manual: igual que #5 | 200, 404 | |
| 7 | GET | `/company/{companyId}` | `@PreAuthorize ownsCompany(#companyId)` | 200, 403 | |

### Máquina de estados real (sin guardas)

`Payment` tiene `PENDING → {COMPLETED, REFUNDED, FAILED}` **sin ninguna validación de estado previo**:

- `complete(txRef)`: `status=COMPLETED`, `transactionReference=txRef`, `paidAt=now`. Sin guarda.
- `refund()`: `status=REFUNDED`. Sin guarda, sin motivo ni fecha.
- `fail()`: `status=FAILED`. **Ninguna ruta lo invoca** → `FAILED` es un estado muerto hoy.

## 2. Hallazgos (bugs reales, NO corregidos en este ticket)

| ID | Hallazgo | Evidencia (test) | Dueño sugerido |
|---|---|---|---|
| **F1** | `createPayment`: el `@PreAuthorize` corre antes del null-check manual, así que un `companyId` null **nunca** llega al 400 documentado — siempre 403 | `createWithANullCompanyIdIsForbiddenNotBadRequest` | T23-B |
| **F2** | `createPayment` no valida `order.status`: se puede registrar un pago para una orden `CANCELLED` (T01-A row 68) | `createIsAllowedAgainstACancelledOrder` | T23-B |
| **F3** | `complete` de un pago cuya orden fue `CANCELLED` → `FuelOrder#markPaid()` lanza `IllegalStateException` crudo → **500** (no 409) | `completingAPaymentWhoseOrderWasCancelledReturns500` | T23-B |
| **F4** | `refund` sin guarda, sin motivo/fecha, y **no** revierte la orden (queda `PAID`) mientras el pago queda `REFUNDED` | `refundIsStateIdempotentButKeepsNoReasonAndLeavesTheOrderPaid`, `refundAPendingPaymentSucceeds` | T23-B |
| **F5** | `complete` repetido no es un no-op estricto (sobrescribe `transactionReference`/`paidAt`) y `complete` después de `refund` es aceptado | `completeIsNotAFullNoOpOnRetryAndOverwritesTheReference`, `completeAfterRefundIsAccepted` | T23-B |
| **F6** | `GET /payments` exige `ROLE_ADMIN`, pero el modelo de roles solo permite `ROLE_BUYER`/`ROLE_PROVIDER` (`iam/domain/model/valueobjects/Roles.java`) → ningún principal puede tenerlo; la ruta es inalcanzable (403 siempre) | `listAllPaymentsRequiresARoleThatNoPrincipalCanHold` | Producto/seguridad (¿admin separado?) |
| **F7** | Dinero en `Double amount`, sin moneda ni escala/redondeo | (lectura de esquema) | T23-B |
| **F8** | Cardinalidad 1:1 order→payment solo en la aplicación (`findByOrderId`); **no** hay unique constraint en `payments.order_id`, y `create` **no es `@Transactional`** → doble creación concurrente posible | (lectura de código + esquema) | T23-B |
| **F9** | `payment/interfaces/rest/PaymentController.java` es una clase vacía (marcador muerto) | (lectura) | T24-A — **resuelto: clase eliminada** |

## 3. Auditoría de datos y consumers

**Escritores de `payments`:** solo `PaymentCommandServiceImpl` (create/complete/refund). Nadie más escribe la
tabla.

**Lectores de `payments`:**

- `payment/**` (sus propias rutas) vía `PaymentQueryService`.
- **`reporting`** (`AnalyticsQueryServiceImpl`): el único consumer cross-module. Deriva **revenue/gasto
  mensual** sumando pagos `COMPLETED` (`ProviderAnalytics.revenue`, `BuyerAnalytics.totalSpent`,
  `PlatformSummary.totalRevenue`) y agrupa por `paidAt`. Es decir, **`COMPLETED` se trata hoy como ingreso
  real** — exactamente el riesgo "sobreafirmar ingresos" del roadmap.

**Consumers externos (frontend/reporting fuera del repo):** no hay evidencia verificable en el repositorio
(no hay client repos, fixtures de contrato ni telemetría de consumo). Queda **UNKNOWN** y se reconcilia en
T22-A; no se inventan consumidores.

## 4. ADR — Decisión U12: qué significa un pago en PrimeFuel

**Contexto:** hay agregado, persistencia y 7 rutas REST, pero **no existe pasarela de pago, webhook, SDK,
`externalPaymentId`, moneda ni estado de settlement**. `transactionReference` lo envía el request y **no
prueba** un cobro bancario. Por lo tanto `COMPLETED` **no puede** significar "dinero liquidado en el banco".

**Decisión:** se adopta la **Opción A — registro financiero operativo** (la recomendada provisionalmente por el
roadmap). `payment` se conserva como bounded context y se define el vocabulario **sin afirmar settlement**:

| Término | Significado en este proyecto (decidido) |
|---|---|
| **registered** | Existe una fila `payments` para una orden, creada por la **company compradora dueña**. Registra una *intención/acuerdo* de pago. Corresponde al estado actual `PENDING`. |
| **authorized** | Un actor autorizado (**company compradora** o **provider de la orden**) confirmó el registro con una referencia externa (`transactionReference`). Es una **confirmación manual**, NO liquidación bancaria. Corresponde al estado actual `COMPLETED`. |
| **settled** | Dinero efectivamente conciliado/liquidado con un banco o pasarela. **HOY NO ES REPRESENTABLE** y el sistema no debe afirmarlo: no hay gateway ni estado de settlement. Queda reservado a una spec futura (Opción B). |
| **refunded** | Un actor autorizado marcó el registro como reembolsado. Hoy **no** revierte la orden ni conserva motivo/fecha. Corresponde al estado actual `REFUNDED`. |

**Consecuencias:**

- `COMPLETED` debe leerse en todo el sistema (incluida la analítica) como **"registro confirmado manualmente"**,
  no como ingreso liquidado. Renombrar la métrica de revenue o exigir settlement confirmado es trabajo de
  T23-B / una spec financiera, no de este ticket.
- `FAILED` no tiene significado operativo hoy (ninguna ruta lo produce); no se le asigna semántica nueva.
- La interfaz pública de `payment` debe eventualmente separar **registrar / confirmar / reembolsar / consultar
  tenant / auditoría de plataforma** (hoy mezclados, con permisos en el controller).

**Fuera de alcance (NO se decide acá):** integrar pasarela de pago, facturación tributaria, cuentas por cobrar,
o borrar `payments`.

## 5. Estado de los invariantes (S23) — caracterizados, no implementados

| Invariante | ¿Se cumple hoy? | Evidencia |
|---|---|---|
| Una transición valida el estado previo | **No** | `completeAfterRefundIsAccepted`, `refundAPendingPaymentSucceeds` |
| `refund` solo aplica a `COMPLETED` y conserva motivo/fecha | **No** | `refundIsStateIdempotentButKeepsNoReasonAndLeavesTheOrderPaid` |
| Completar/reembolsar repetido es idempotente | **Parcial**: el estado es idempotente, pero `complete` sobrescribe `transactionReference`/`paidAt` | `completeIsNotAFullNoOpOnRetryAndOverwritesTheReference` |
| `amount` positivo y consistente con el snapshot comercial | **Parcial**: consistencia (`amount == order.totalPrice`) solo en el controller; positividad no se valida; nada en el application layer | `createRejectsAnAmountThatDoesNotMatchTheOrderTotal` |
| Tenant derivado del principal, no del body | **No**: `companyId` viene del body y el principal solo lo verifica | `createRequiresTheBuyerCompanyAndCannotBeCalledByTheProvider` |
| Una entrega puede completarse aunque el pago esté pendiente | **Sí** (delivery no lee payment) | `aDeliveryCanBeCompletedWhileItsPaymentIsStillPending` |

## 6. Inventario de accesos directos de `payment` a otros módulos (para T23-B)

| Archivo | Import | Tipo | Acción en T23-B |
|---|---|---|---|
| `payment/application/internal/commandservices/PaymentCommandServiceImpl.java` | `ordering.domain.repositories.FuelOrderRepository` | **escritura cross-domain** (`order.markPaid()` + save) | **Quitar**; mover el efecto a un adapter/compatibilidad (S23: `DeliveryCompleted`/`PaymentCompleted` como hechos independientes) |
| `payment/interfaces/rest/PaymentsController.java` | `ordering.application.queryservices.FuelOrderQueryService` (+ `GetFuelOrderByIdQuery`) | lectura | Sustituir por una interface pública (o mover la verificación de ownership al application layer) |
| `payment/interfaces/rest/PaymentsController.java` | `iam.infrastructure.authorization.sfs.services.CurrentUserAccess` | dependencia a `iam.infrastructure` | Sustituir por `iam.api.TenantAccess` |

## 7. Test plan cubierto

`PaymentCharacterizationTest` (18 tests, H2, MockMvc, fixtures por tenant): creación OK/duplicada/amount
incorrecto/orden inexistente/company ajena/null companyId/orden cancelada; complete OK/repetido/después de
refund/referencia vacía/orden cancelada; refund PENDING/repetido/después de complete (orden queda `PAID`);
permisos buyer/provider/stranger; GET `/payments` admin inalcanzable; reads scoping; delivery completable con
pago `PENDING`.

## 8. Build

`./mvnw.cmd test` (JAVA_HOME `C:/Users/crama/.jdks/openjdk-26.0.2`): **verde**. Sin cambios de esquema (no hay
migración) y sin cambios bajo `src/main/java/.../payment`.
