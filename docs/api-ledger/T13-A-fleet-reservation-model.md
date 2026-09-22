# T13-A — Modelo y cálculo de capacidad (S13)

Parent spec: S13. Preconditions: T12-B, T11-B, T03-B (hechos). **U05/U06 resueltas** (2026-09-22) — ver
`docs/PRIMEFUEL_MIGRATION_ROADMAP.md`, nota inmediatamente posterior al *Definition of Done* de S13.

> **Build no verificado en esta máquina — pendiente de verificación por el usuario.** Sin SDK de Java: no se
> ejecutó `compile`, `test` ni el arranque MySQL. El código, la migración `V18` y los tests quedan escritos
> para que el usuario corra `mvnw test` y valide el esquema con `scripts/validate-schema-mysql.ps1`.

> **Alcance:** T13-A **modela** la reserva de flota (ventana + volumen), elige el lock/constraint y crea la
> migración aditiva. La reserva **concurrente de punta a punta** (dos carreras → una gana) y el ciclo de
> **release/expiry** son T13-B.

## Decisiones heredadas (U05/U06)

- **U05 — precisión/unidad.** La *capacidad utilizable* se modela con los `shared.domain.model.valueobjects.Unit`
  / `Volume` (los mismos de T11-A) sobre el `Double` legacy, **sin** migrar a decimal. La capacidad del
  tanker y el volumen reservado se comparan convirtiendo de unidad (`Volume.atLeast`).
- **U06 — lock/constraint.** Se usa el mismo patrón `PESSIMISTIC_WRITE` de T11-B, aplicado **sobre el recurso
  reservado (driver y tanker), no un lock global**: `DriverPersistenceRepository.lockById` y
  `TankerPersistenceRepository.lockById` toman lock de fila. El **overlap de ventana se revalida dentro de la
  misma TX** que toma el lock (`FleetReservationServiceImpl.reserve`). Orden de lock fijo (driver, luego
  tanker) para que dos llamadas que comparten recurso serialicen en vez de bloquearse en deadlock.

## Qué se agregó

- **Agregado `FleetReservation`** (`fleet.domain.model.aggregates`): `providerId`, `driverId`, `tankerId`,
  `reference`, `ReservationWindow`, `Volume`, `FleetReservationStatus` (`ACTIVE/RELEASED/EXPIRED`), `version`.
  Invariantes en el constructor: mismo tenant, volumen positivo, ventana válida.
- **VOs** `ReservationWindow` (ventana semiabierta `[start, end)`, `overlaps` = `start < other.end && end >
  other.start`) y `FleetReservationStatus`.
- **Comando** `ReserveFleetCommand(providerId, driverId, tankerId, reference, windowStart, windowEnd, volume,
  unit)`.
- **Seam `fleet.api.FleetReservations`** con `reserve(...) → Result<ReservationSnapshot, ApplicationError>` e
  impl `FleetReservationServiceImpl` (`fleet.infrastructure.services`): valida, toma lock de driver+tanker,
  chequea capacidad y revalida overlap.
- **Persistencia**: entidad `fleet_reservations` + repositorio de dominio + Spring Data + assembler + adapter.
- **Migración aditiva `V18__fleet_reservations.sql`**: tabla `fleet_reservations` (`provider_id`, `driver_id`,
  `tanker_id`, `reference`, `window_start`, `window_end`, `volume`, `unit`, `status`, `version`, auditoría),
  único `uk_fleet_reservations_reference` sobre `reference` e índice de recurso/ventana.

## Reglas de cálculo

- **Capacidad utilizable ≥ volumen.** `capacity.atLeast(volume)` con conversión de unidad; igualdad pasa,
  volumen superior → **409** (`FleetReservation_CONFLICT`).
- **Sin overlap.** Una reserva `ACTIVE` del mismo driver **o** tanker con ventana que se solape (semiabierta)
  → **409**. Ventanas que solo se tocan (`a.end == b.start`) **no** se solapan.
- **Mismo tenant.** Un driver/tanker de otro proveedor → **404** (`*_NOT_FOUND`), nunca una reserva cruzada.

## Asunciones abiertas

- **A1 — sin endpoint REST v2.** S13 dice "REST v2 solo si operación humana lo requiere"; no se agregó ninguna
  ruta (la reserva se consume por `fleet.api`, como el resto de S12). Si aparece la necesidad humana, es un
  ticket de controller.
- **A2 — release/expiry y concurrencia son T13-B.** El agregado modela `release()` (guarda contra doble
  release) pero **no** se expone operación de release ni expiry; el `@Version` + lock + revalidación quedan
  listos, pero la prueba de carrera (dos concurrentes → una gana) y el release/expiry idempotentes son T13-B.
- **A3 — `reference` es clave de idempotencia "cableada", no aplicada.** La columna y el único existen, pero
  la semántica de reintento idempotente por `reference` es T13-B.
- **A4 — `ReserveFleetCommand.providerId` no se resuelve todavía desde `TenantAccess`** (revisión del
  orquestador, 2026-09-22). Es un campo plano del comando; hoy no es explotable porque no hay endpoint REST
  en esta tanda (A1), pero **T13-B debe resolver `providerId` desde `iam.api.TenantAccess.currentProviderId()`
  en el controller/capa que exponga la reserva**, igual que ya hacen `fleet`/`supply`, y nunca confiarlo del
  body del comando.

## Tests

- `ReservationWindowTest` (unit): ventana inválida (end≤start, nulos), solape parcial/containment, ventanas
  contiguas que no solapan, disjuntas.
- `FleetReservationTest` (Spring + H2, vía `fleet.api`): reserva válida (`ACTIVE`, volumen/ventana),
  capacidad igual (pasa) y superior (409), conversión de unidades (gallones vs litros), overlap por driver y
  por tanker, ventanas contiguas y recursos distintos permitidos, recurso de otro tenant (404), ventana
  inválida y volumen no positivo (400). **No hay prueba de concurrencia** (T13-B).
