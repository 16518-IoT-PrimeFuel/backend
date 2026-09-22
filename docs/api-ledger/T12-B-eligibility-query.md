# T12-B — Política y consulta de elegibilidad (S12)

Parent spec: S12. Precondition: T12-A (done). **Cierra S12.**

> **Build no verificado en esta máquina — pendiente de verificación por el usuario.** Sin SDK de Java: no
> se ejecutó `compile`, `test` ni el arranque MySQL. El código y los tests quedan escritos para que el
> usuario corra `mvnw test` y valide el esquema con `scripts/validate-schema-mysql.ps1`.

> Este ticket reemplaza a `T12-B-eligibility-BLOCKED.md` (U07 se resolvió el **2026-09-22** por decisión
> de producto, registrada junto a S12 en el roadmap). El doc de bloqueo se elimina.

## Decisión aplicada (U07, 2026-09-22)

`fleet.api.EligibilityQuery` responde **solo** "¿puede sugerirse este recurso a este tenant?":

- **elegible** = estado permitido (`AVAILABLE`) **+** `active=true` **+** mismo tenant que consulta;
- `SUSPENDED` / `MAINTENANCE` / `INACTIVE` → **no elegible**;
- `ASSIGNED` / `IN_ROUTE` → **ocupado**: no se sugiere, pero no es lo mismo que inelegible;
- **sin vigencia/fecha de vencimiento** (no se agregaron campos de expiración);
- **sin rol nuevo**: se autoriza con `iam.api.TenantAccess` (provider dueño), igual que los controllers
  de `fleet` desde T12-A.

### Por qué el resultado es de tres valores y no booleano

El roadmap (S12, acceptance criteria) pide "suspendido/vencido no se sugiere", y U07 distingue además el
caso "ocupado". Modelarlo como booleano obligaría a tratar un driver simplemente asignado como
"inelegible", lo que ocultaría que **no requiere ninguna acción administrativa**: vuelve a ser elegible
solo con que su estado cambie. Por eso `Outcome` tiene `ELIGIBLE` / `BUSY` / `INELIGIBLE`, y el `reason`
explica el porqué (`available`, `busy (assigned)`, `disabled`, `status SUSPENDED`, `unknown status X`).

## Superficie añadida

`fleet.api.EligibilityQuery` (contrato público) + `fleet.infrastructure.services.EligibilityQueryImpl`:

- `assessDriver(providerId, driverId)` / `assessTanker(providerId, tankerId)` → `Optional<…Assessment>`;
  **vacío** si el recurso no existe o pertenece a otro tenant (nunca se filtra un recurso ajeno);
- `eligibleDrivers(providerId)` / `eligibleTankers(providerId)` → solo los `ELIGIBLE` del tenant.

REST v2 (mismos permisos que el resto de `fleet`; tenant desde el principal):

- `GET /api/v2/drivers/eligible`, `GET /api/v2/drivers/{driverId}/eligibility`;
- `GET /api/v2/tankers/eligible`, `GET /api/v2/tankers/{tankerId}/eligibility`;
- recurso `EligibilityResource { outcome, reason }`.

**Alcance respetado:** la query **solo filtra** (estado + activo + tenant). No hay routing, ranking,
scoring ni ordenamiento de conductores/cisternas — eso queda explícitamente fuera de S12.

## v1

No se adapta v1: la elegibilidad es una capacidad nueva y no existe endpoint v1 que la exponga. Los
adapters v1 de `drivers`/`vehicles` (T12-A) siguen intactos.

## Tests

- `EligibilityQueryTest` (Spring + H2): `AVAILABLE` elegible; `ASSIGNED` → `BUSY` (y fuera de la lista de
  elegibles); `SUSPENDED` inelegible; `active=false` inelegible aunque el estado sea `AVAILABLE`; reglas
  equivalentes para tanker (`AVAILABLE`/`IN_ROUTE`/`MAINTENANCE`); un recurso de otro tenant no es
  evaluable ni aparece en su lista.
- `FleetV2ControllerTest` (MockMvc, ver T12-A): además del ciclo disable/enable por HTTP y el 404
  cross-tenant, verifica `ELIGIBLE`/`BUSY`/`INELIGIBLE` y la lista `/eligible` sobre HTTP.

## Asunciones abiertas

- **A1 — estado desconocido ⇒ `INELIGIBLE`.** Las filas legadas pueden tener `status` fuera del enum
  tipado; en vez de asumir elegibilidad se reporta `unknown status X`. Revisar si aparece en producción.
- **A2 — `BUSY` no bloquea una futura reserva.** Cuando S13/T13-A implemente `FleetReservation` con
  exclusividad real, la semántica "ocupado" pasa a la reserva; hoy `BUSY` es solo informativo.
- **A3 — sin expiración.** Si producto define vigencia más adelante (campos/licencia), será un cambio de
  U07 que agrega el predicado; la forma de `EligibilityQuery` no cambia.
- **A4 — permisos finos por transición** (p. ej. "solo el fleet manager") no existen: `U07` resolvió que
  alcanza con el provider dueño, y el modelo IAM actual solo distingue provider/buyer.
