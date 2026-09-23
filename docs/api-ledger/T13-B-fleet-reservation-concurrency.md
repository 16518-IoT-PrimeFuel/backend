# T13-B — Reserva concurrente y liberación (S13)

Parent spec: S13. Precondición: **T13-A** (`9411b9a feat: add fleet reservation model with pessimistic
locking`). Hereda **U05** (unidades `Unit`/`Volume`, sin decimal) y **U06** (lock `PESSIMISTIC_WRITE` sobre
driver/tanker, revalidación de overlap en la misma TX, orden de lock fijo driver→tanker) de T11-A/T11-B.

> **Alcance:** T13-A **modeló** la reserva; T13-B cierra las cuatro asunciones abiertas (A1–A4) y prueba la
> **carrera real** contra MySQL 8.0.46. No se tocó el esquema: `V18` se reutiliza tal cual.

## Decisiones y asunciones resueltas

- **A1 — no se expone endpoint REST v2.** El roadmap dice "REST v2 solo si operación humana lo requiere" y
  T13-A ya no agregó ninguna ruta. El siguiente consumidor en el DAG es **T15-A** (asignación transaccional),
  que llama `fleet.api.FleetReservations` directamente desde `applicationflows`, sin pasar por HTTP. Por eso
  no se agrega controller ni se toca el snapshot OpenAPI. Si aparece la necesidad humana, es un ticket de
  controller aparte.
- **A4 — `providerId` nunca se confía del request.** Consecuencia directa de A1: al no haber endpoint, no hay
  superficie HTTP que reciba `providerId`. El contrato del seam queda explícito: **el caller resuelve
  `providerId` desde `iam.api.TenantAccess.currentProviderId()`** (igual que `fleet`/`supply`); el comando
  solo lo transporta. Si T15-A/otro consumidor lo expusiera por REST, el controller debe resolverlo del
  principal y responder **403** cuando `TenantAccess` no puede resolver el provider — nunca leerlo del body.
- **A2 — release/expiry expuestos por el seam, deterministas.**
  - `FleetReservations.release(reference)`: **idempotente**. Reserva `ACTIVE` → `RELEASED`; reserva ya
    terminal → devuelve el mismo snapshot sin transicionar (no hay doble release ni estado corrupto);
    reference desconocido → `FLEETRESERVATION_NOT_FOUND`. La guarda estricta del agregado (`release()` lanza
    si no es `ACTIVE`) se conserva como invariante de dominio; la idempotencia la aplica el servicio.
  - `FleetReservations.expireOverdue()`: barre las reservas `ACTIVE` cuya ventana semiabierta ya terminó
    (`now >= windowEnd`) y las pasa a `EXPIRED`. Es **determinista** (usa el `java.time.Clock` inyectado,
    mismo patrón que T08/T09) e **idempotente** (una segunda pasada devuelve 0). La transición vive en el
    agregado como `expireIfPast(Instant now)` (devuelve si cambió, y nunca toca `RELEASED`/`EXPIRED`).
  - No hace falta expirar dentro de `reserve`: el predicado de overlap (`windowStart < newEnd && windowEnd >
    newStart`) ya ignora ventanas pasadas para una reserva futura, así que un `ACTIVE` vencido no genera un
    falso `409`.
- **A3 — idempotencia por `reference`.** El único `uk_fleet_reservations_reference` ya existía. La semántica
  ahora es: reintentar `reserve()` con el mismo `reference` **devuelve la misma reserva** (mismo `id`,
  estado actual) si los parámetros coinciden (provider/driver/tanker/ventana/volumen/unidad) y **`409`**
  (`FLEETRESERVATION_CONFLICT`) si no coinciden.
  - La lectura por `reference` se hace **después** de tomar los locks y dentro de una TX **`READ_COMMITTED`**
    (ver abajo). Así un reintento que compitió con el original —y quedó esperando el lock— ve la reserva
    ya commiteada y la reproduce, en vez de chocar contra el unique constraint.

## Concurrencia (el corazón del ticket)

- **Lock de recurso, no global (U06).** `reserve` toma `PESSIMISTIC_WRITE` sobre la fila del **driver** y
  luego la del **tanker**, siempre en ese orden fijo. Dos llamadas que comparten un recurso se serializan en
  la primera fila que comparten; el orden fijo evita el ciclo de espera (deadlock) que aparecería con órdenes
  opuestos.
- **Revalidación dentro de la TX.** El chequeo de solape corre tras el lock, en la misma transacción, así que
  el ganador ve el estado del perdedor y responde `409` sin dejar estado a medias.
- **`READ_COMMITTED` deliberado.** El snapshot por sentencia garantiza que la lectura de `reference` post-lock
  observe lo que commiteó el que tenía el lock antes; con `REPEATABLE_READ` (default de MySQL) el perdedor
  podría no ver la fila y chocar contra el único. Se documenta como decisión.
- **Fallo a mitad de operación.** Si algo falla después de tomar los locks (p. ej. el `save`), la TX hace
  rollback: InnoDB libera las filas y no queda reserva a medias. Cubierto por inyección de fallo.

## Tests

- `FleetReservationLifecycleTest` (unit): `release()` es terminal y guarda contra doble release;
  `expireIfPast` es determinista e idempotente (borde `now == windowEnd` = vencida).
- `FleetReservationTest` (Spring + H2, vía `fleet.api`): los tests de T13-A **más** replay idempotente por
  `reference` (misma reserva, sin segunda fila), replay con parámetros distintos → `409`, release idempotente
  que libera la ventana, release de reference desconocido → `*_NOT_FOUND`, y `expireOverdue` idempotente.
- `FleetReservationConcurrencyTest` (H2, smoke): dos hilos en una barrera compiten por el mismo
  driver+tanker con ventanas solapadas; exactamente uno gana y el otro recibe `409`, sin dejar holds
  duplicados. H2 solo prueba el camino; las semánticas de lock de fila son de MySQL.
- `FleetReservationFailureInjectionTest` (H2, inyección): se fuerza un fallo en el `save` **después** de
  tomar los locks; el rollback no deja reserva y el mismo recurso se puede reservar inmediatamente (los locks
  se liberaron, no quedaron colgados).
- `FleetReservationConcurrencyMySqlTest` (**MySQL 8.0.46, gated** por `FLEET_MYSQL_IT=1` + `MYSQL_*`): la
  carrera real entre dos conexiones sobre el mismo recurso solapado → **una gana, la otra `409`**, sin
  deadlock (los futures resuelven dentro del timeout) y con una única fila `ACTIVE`; además valida el esquema
  Flyway al levantar (`ddl-auto=validate`) y prueba release/expiry idempotentes sobre MySQL. Se deja gated
  para que `mvnw test` siga hermético; se corre aparte con credenciales locales.

## Estado del build

- `./mvnw.cmd test` (JAVA_HOME = `C:/Users/crama/.jdks/openjdk-26.0.2`): **156/156 verde**, 2 **skipped** =
  los dos tests del IT de MySQL (gated). Sin cambios de esquema → `V18` sin modificar.
