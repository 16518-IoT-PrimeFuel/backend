# T23-B — Aislamiento de Payment y estado físico (S23)

Depende de T23-A. Aplica la decisión U12 (registro financiero operativo) y **quita el acceso cross-domain** de
`payment`, conservando las 7 rutas v1 y la tabla histórica.

## 1. `payment` ya no escribe `ordering`

- `PaymentCommandServiceImpl` **ya no importa `FuelOrderRepository`** (el acceso que T23-A inventarió). Eliminado.
- `complete` ya no muta la orden: marca el pago `COMPLETED` y **publica `payment.completed.v1`** (outbox +
  envelope in-process, como T20-A).
- Nuevo adapter **`ordering/application/internal/consumers/OrderingPaymentCompletionAdapter`**: reacciona a
  `payment.completed.v1` y marca **su propia** agregado `FuelOrder` como `PAID`. Así el contrato v1 (la orden
  llega a `PAID`) se preserva **sin que payment conozca la persistencia de ordering**.
- `PaymentCompleted` y `DeliveryCompleted` son **hechos independientes**: delivery nunca lee payment (ya era
  cierto desde T14-B) y payment nunca toca el estado físico.

## 2. Invariantes movidos al application layer

`PaymentCommandServiceImpl.handle(CreatePaymentCommand)` ahora valida (antes vivían sólo en el controller):
order y company presentes; la orden existe y pertenece a esa company (404); `amount` **positivo y consistente**
con el snapshot comercial de la orden (400); a lo sumo un pago por orden (409, chequeo de aplicación).

Para leer la orden sin depender de `ordering.domain` se agregó el seam público **`ordering.api.OrderLookup`**
(snapshot), consumido por payment (servicio y controller). Esto además **elimina** la dependencia previa de
`payment` a `ordering.domain.model.queries`.

## 3. Hallazgo F1 corregido

`PaymentsController.createPayment`: los chequeos de presencia corren **antes** de la autorización, así que un
`companyId`/`orderId` nulo responde el **400** documentado (antes siempre 403 por el `@PreAuthorize` que corría
primero). La propiedad de la company declarada se verifica explícitamente (403). El controller también dejó de
usar `iam.infrastructure.CurrentUserAccess` y pasó a `iam.api.TenantAccess`.

> T23-A caracterizó F1 como 403; `PaymentCharacterizationTest` se actualizó a 400 (caracterización superada por
> la corrección). Los demás comportamientos v1 quedan iguales.

## 4. Guard + tests

- **`PaymentForeignRepositoryGuardTest`** (nuevo): exige **cero** imports de `payment` a
  `domain.repositories`/`infrastructure` de otro módulo (cubre el criterio "cero acceso directo de payment a
  FuelOrderRepository").
- **`PaymentOrderDecouplingTest`** (nuevo): completar un pago marca la orden `PAID` **a través del evento**
  publicado; crear un pago no toca la orden y no publica nada.
- `PaymentCharacterizationTest` (18) sigue verde con el ajuste de F1; `OrderFulfillmentGoldenPathTest`,
  `StateLifecycleRetryCharacterizationTest`, `ModuleBoundaryRulesTest` verdes.

## 5. No incluido (documentado, fuera del alcance de este corte)

Quedan como trabajo posterior (no bloquean logística): guards de transición e idempotencia estricta de
`complete`/`refund` (T23-A F4/F5), motivo/fecha de reembolso, `BigDecimal`+moneda, `unique(order_id)`, y la
separación de permisos `registrar/confirmar/reembolsar/consultar/admin`. El ADR (T23-A) los deja definidos como
siguientes pasos.

## Build

`./mvnw.cmd test`: **196/196 verde** (4 skipped = IT MySQL gated). Sin cambios de esquema.
