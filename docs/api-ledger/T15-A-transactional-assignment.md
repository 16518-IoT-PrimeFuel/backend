# T15-A — Interfaz de asignación transaccional (S15)

Parent spec: S15. Dependencias hechas: T10-B, T11-B, T13-B, T14-B.

> **Alcance:** unir aceptación, reservas y creación de delivery detrás de interfaces públicas, en **una TX
> local desde el composition root** (`applicationflows`). `AssignDelivery` orquesta `ConsumeAcceptance` →
> `ReserveSupply` → `ReserveFleet` → crear el delivery **asignado**. Fuera de alcance: optimización global de
> asignación (solo correcto y atómico).

## Componentes nuevos

- **`applicationflows`** (composition root, sin reglas ni persistencia propias):
  - `AssignDeliveryFlow` — entrada pública **no transaccional**; adapta el fallo a `Result`.
  - `AssignDeliveryExecutor` — mitad **transaccional** (`@Transactional(isolation = READ_COMMITTED)`); corre
    los cuatro pasos y **lanza** `AssignmentFailedException` ante fallo (ver "Compensación").
  - `AssignDeliveryFlowCommand` / `AssignDeliveryResult`.
  - `DeliveryAssignmentController` — `POST /api/v2/deliveries` (create v2) + recursos.
- **Seams públicos consumidos** (nadie toca repositorios ajenos):
  - `replenishment.api.ReplenishmentAcceptance.consume(requestId)` (nuevo) + `ReplenishmentLookup.findByOrderId`.
  - `supply.api.SupplyReservations` (nuevo, sobre las reservas de T11-B).
  - `fleet.api.FleetReservations` (T13-B, ya existía).
  - `fulfillment.api.DeliveryAssignments` (nuevo) — crea el delivery **ASSIGNED** y lo enruta por la máquina
    física (T14-A) para que el estado, su journal y el evento `DeliveryAssigned` salgan una sola vez.

## Invariantes y cómo se cumplen

- **Aceptación vigente y once-only.** El flujo resuelve la `ReplenishmentRequest` correlacionada al order
  (`findByOrderId`), exige `ACCEPTED` y llama `consume` **una sola vez`. Un segundo intento recibe
  `REPLENISHMENTREQUEST_CONFLICT` (aceptación ya consumida). Un order **sin** request aceptada → **404**
  (`REPLENISHMENTREQUEST_NOT_FOUND`): así un pedido legacy sin aceptación no entra por v2.
- **Reservas exclusivas / fallo revierte todo (compensación).** Los cuatro pasos corren en una sola TX. Un
  fallo **no** se devuelve como `Result` (eso commitearía la TX) sino que se **lanza** y la TX hace rollback:
  se revierten la aceptación consumida y las dos reservas. Es decir, *compensar = rollback*; no hay estado a
  medias que reparar. Cubierto por failure injection en cada paso.
- **Asignar ≠ iniciar.** El delivery se crea en `ASSIGNED`; `start/arrive/complete` siguen siendo de T14-A.
- **Idempotencia por `commandId`.** El delivery guarda `assignment_command_id` (columna nueva, única) y el
  `commandId` es además la `reference` de ambas reservas. Un retry devuelve el **mismo** delivery sin volver a
  reservar (fast-path `findByAssignmentCommandId`); si el mismo `commandId` llega con otro `orderId` → 409.

## Decisiones / asunciones

- **A1 — contrato de create v2.** `POST /api/v2/deliveries` recibe `{ commandId, orderId, driverId, tankerId,
  windowStart, windowEnd, scheduledDate, notes }`. **No** recibe `providerId`: se resuelve de
  `iam.api.TenantAccess.currentProviderId()` (nunca del body). Si el principal no es un provider → **403**; si
  el request aceptado detrás del order no pertenece a ese provider → **404** (no se filtra la existencia).
- **A2 — el order debe tener una request aceptada correlacionada.** `deliveries.order_id` es `NOT NULL` y las
  requests `v2` nativas no crean order (T10-B: "sin orden directa v2"). Por eso el flujo exige que la
  aceptación esté correlacionada con un order (la ruta legacy de aceptación sí lo hace). Asignar requests
  v2-nativas sin order queda explícitamente fuera de alcance hasta que exista ese correlato.
- **A3 — `READ_COMMITTED`.** El executor fija `READ_COMMITTED` para que la reserva de flota anidada (T13-B)
  corra con la misma isolation que espera al releer su clave de idempotencia tras el lock (en `REPEATABLE_READ`
  el perdedor no vería la fila y chocaría con el único).
- **A4 — sugerir/confirmar.** "Sugerir" reutiliza `fleet.api.EligibilityQuery` (T11-A/T12-B, ya publicado); no
  se reimplementa ranking (fuera de alcance). "Confirmar" es `AssignDeliveryFlow`.
- **T15-B** es quien retira los repositorios ajenos de `DeliveryCommandServiceImpl` y enruta el create v1; aquí
  el create v1 queda **intacto y con su semántica actual**.

## Esquema

- **`V19__delivery_assignment_command.sql`**: `deliveries.assignment_command_id varchar(120)` + único
  `uk_deliveries_assignment_command_id` (nullable: los deliveries legacy no tienen commandId; MySQL permite
  múltiples NULL en un único). Validada en MySQL 8.0.46 con `scripts/validate-schema-mysql.ps1`.

## Tests

- `AssignDeliveryFlowTest` (H2): happy path (delivery `ASSIGNED` + reservas + aceptación consumida), retry con
  el mismo `commandId` (no-op, una sola reserva), order sin request aceptada → 404 sin consumo; **failure
  injection por paso** — supply falla (stock insuficiente) → aceptación sin consumir, sin reserva de flota, sin
  delivery; flota falla (tanker chico) → aceptación sin consumir y reserva de supply revertida; y una **carrera**
  H2 que produce exactamente una asignación.
- `AssignDeliveryFlowConcurrencyMySqlTest` (**MySQL 8.0.46, gated** `ASSIGN_MYSQL_IT=1`): dos asignaciones
  compiten por el mismo driver+tanker+ventana → **una gana**, la otra revierte (aceptación del perdedor sin
  consumir, sin reserva huérfana), sin deadlock. Levantar el contexto aplica `V19` + `validate`.

## Estado del build

- `./mvnw.cmd test` (JAVA_HOME = `C:/Users/crama/.jdks/openjdk-26.0.2`): **163/163 verde**, 3 skipped = los IT
  de MySQL gated (2 de T13-B + 1 de T15-A).
- **MySQL 8.0.46 real:** `AssignDeliveryFlowConcurrencyMySqlTest` **verde**; `validate-schema-mysql.ps1`
  **OK** (Flyway aplica V1–V19 y Hibernate `validate` pasa).

## Nota (fuera de alcance)

La creación concurrente *por primera vez* de la fila mutex de stock (`supply_stock_locks`,
`SupplyStockLockInitializer.ensureExists`, T11-B) puede lanzar `UnexpectedRollbackException` si dos TX la crean
a la vez (la suya `REQUIRES_NEW` queda rollback-only al capturar la violación de único). No es de T15-A; los
tests lo evitan pre-creando la fila (warmup), igual que T11-B. Queda anotado para un ticket futuro.
