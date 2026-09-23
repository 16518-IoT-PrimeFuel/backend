# T15-B — Eliminar accesos cruzados y probar carreras (S15)

Parent spec: S15. Dependencia: T15-A (el orquestador `AssignDeliveryFlow`/`AssignDeliveryExecutor` en
`applicationflows`).

> **Alcance:** retirar los repositorios ajenos de `fulfillment` y enrutar el `create` v1 por el mismo
> composition root, sin cambiar la API v1. Fuera de alcance: rediseño de la máquina de estados física (S14) o
> del pago.

## Qué se eliminó

`fulfillment` ya **no importa ningún repositorio ajeno**. Los que existían en
`DeliveryCommandServiceImpl` (y el servicio interno extraído del controller) eran:

| Antes | Dueño | Ahora |
|---|---|---|
| `fleet.domain.repositories.DriverRepository` | fleet | `applicationflows.LegacyDeliveryExecutor` |
| `fleet.domain.repositories.TankerRepository` | fleet | idem |
| `inventory.domain.repositories.FuelProductRepository` | inventory | idem |
| `ordering.domain.repositories.FuelOrderRepository` | ordering | idem |
| `equipment.domain.repositories.EquipmentRepository` | equipment | idem |
| `iam.infrastructure...CurrentUserAccess` (controller) | iam | `iam.api.TenantAccess` |

Único acceso cross-module que queda en `fulfillment`: `ordering.application.queryservices.FuelOrderQueryService`
(superficie pública de consulta del módulo ordering, **no** un repositorio) para la evidencia de volumen del
cierre v1, y los seams `fleet.api`/`supply.api`/`fulfillment.api`.

## Cómo

- **Puerto `fulfillment.api.DeliveryIntegration`** (nuevo): `createDelivery` (create v1) y
  `applyCompletionEffects` (efectos legacy del complete v1). Implementado en el composition root por
  `applicationflows.LegacyDeliveryIntegration` (no transaccional) → `LegacyDeliveryExecutor`
  (`@Transactional(READ_COMMITTED)`), que **lanza** para revertir todo ante fallo (misma técnica que T15-A).
- **`DeliveryIntegration.createDelivery` es dual-mode** (decisión del usuario):
  - Order con request **`ACCEPTED`** → pasa por `AssignDeliveryExecutor` (el mismo orquestador de reservas
    exclusivas que usa v2) → v1 hereda **la misma garantía de carrera** (bajo el lock de flota) e idempotencia
    por `commandId`.
  - Order **directo** (sin request aceptada) → **rama legacy**: reproduce exactamente los efectos originales
    (driver `ASSIGNED`, vehicle `IN_ROUTE`, descuento de stock, `order.dispatch()`) y crea el delivery legacy
    (`fulfillment.api.DeliveryAssignments.createLegacy`: `DISPATCHED`, `physical_state` nulo, derivado en
    lectura).
  - Ambas ramas despachan el order, por lo que el contrato v1 y el *golden path* a `PENDING_PAYMENT` se
    conservan.
- **Aceptación una sola vez**: en la rama de reservas v1 **no** se vuelve a consumir la aceptación, porque el
  bridge legacy de aceptación (T10-B) ya la consumió al crear el order. Por eso `AssignDeliveryExecutor`
  expone `execute(command, boolean consumeAcceptance)`: v2 pasa `true`, v1 pasa `false`.
- **Complete v1**: el cierre físico sigue en `fulfillment`; sus efectos foráneos (liberar driver/tanker, fin
  de la reserva de flota, recargar tanque, `order.receive()`) se delegan a `DeliveryIntegration` en la misma
  transacción.

## Semántica v1 (documentada)

El contrato HTTP v1 no cambia (rutas, cuerpos, códigos, `status`). SÍ cambian dos comportamientos internos,
deliberados por este ticket:

1. **Reintento del create v1 es idempotente.** El `commandId` se deriva del order (`delivery-create:{orderId}`),
   así que un reintento devuelve el **mismo** delivery en vez de un `409` por "ya existe un delivery para el
   order". (El contrato no tenía un test que fijara el `409` de duplicado.)
2. **Para orders con aceptación, el create v1 retiene reservas** (supply + flota) en lugar de descontar stock y
   mutar el estado del driver directamente. Los orders directos conservan su comportamiento legacy exacto.

**Asunción:** el create v1 no trae ventana temporal; la rama de reservas usa `[now, now+8h]` con el `Clock`
inyectable.

## Tests

- `fulfillment/DeliveryV1OrchestrationTest` (H2): order directo conserva los efectos legacy (`DISPATCHED`,
  `physical_state` nulo, order `DISPATCHED`, stock −qty, driver `ASSIGNED`, sin reserva de supply); retry
  idempotente (mismo delivery, una sola reserva); **carrera v1** por el mismo driver/tanker → exactamente una
  delivery; **rollback** (tanker chico) → sin delivery, sin reserva huérfana, order sigue `PENDING`.
- `architecture/FulfillmentForeignRepositoryGuardTest` (nuevo): escanea las fuentes de `fulfillment` y exige
  **cero** imports a `domain.repositories`/`infrastructure` de otro módulo — la definición de "cero imports a
  repositorios ajenos" de S15, cubriendo también fleet/supply/replenishment que ArchUnit no lista.
- `fulfillment/DeliveryV1OrchestrationConcurrencyMySqlTest` (**MySQL 8.0.46, gated** `ASSIGN_MYSQL_IT=1`): la
  carrera v1 en MySQL real produce exactamente una delivery.
- Contract tests preservados verdes: `OrderFulfillmentGoldenPathTest`, `DeliveryV1LifecycleMappingTest`,
  `StateLifecycleRetryCharacterizationTest`, `CrossTenantIsolationTest`, `AuthorizationContractTest`,
  `ModuleBoundaryRulesTest`.

## Estado del build

- `./mvnw.cmd test` (JAVA_HOME `C:/Users/crama/.jdks/openjdk-26.0.2`): **169/169 verde**, 4 skipped = los IT
  de MySQL gated (2 de T13-B + T15-A + T15-B).
- **MySQL 8.0.46 real** (credenciales mediante `MYSQL_USER`/`MYSQL_PASSWORD`, sin Docker): `DeliveryV1OrchestrationConcurrencyMySqlTest`
  **verde**. Sin cambios de esquema (no hay migración nueva): el esquema MySQL ya se validó en T15-A (`V19`) y
  estos ITs levantan con `ddl-auto=validate` + Flyway.
