# T14-A — Lifecycle y comandos de ejecución de Delivery (S14)

Parent spec: S14. Preconditions: T10-B, T19-B (ambos hechos).

> **Build no verificado en esta máquina — pendiente de verificación por el usuario.** Sin SDK de Java: no se
> ejecutó `compile`, `test` ni el arranque MySQL. El código, la migración `V17` y los tests quedan escritos
> para que el usuario corra `mvnw test` y valide el esquema con `scripts/validate-schema-mysql.ps1`.

> **Alcance:** T14-A **prepara la máquina de estados**. T14-B es quien termina de adaptar v1 (y de
> desconectar la escritura de nivel/pago del camino físico). Aquí v1 **no** se re-enruta.

## Máquina de estados física

`ASSIGNED → STARTED → ARRIVED → DELIVERING → COMPLETED`, más dos salidas terminales explícitas
(`FAILED`, `CANCELLED`). La tabla de transiciones vive en `DeliveryPhysicalState`:

| desde | hacia permitido |
| --- | --- |
| `ASSIGNED` | `STARTED`, `FAILED`, `CANCELLED` |
| `STARTED` | `ARRIVED`, `FAILED`, `CANCELLED` |
| `ARRIVED` | `DELIVERING`, `FAILED`, `CANCELLED` |
| `DELIVERING` | `COMPLETED`, `FAILED`, `CANCELLED` |
| `COMPLETED` / `FAILED` / `CANCELLED` | — (terminales) |

Cualquier transición fuera de la tabla lanza y el servicio la traduce a **409** (`*_CONFLICT`).

El estado físico se guarda en la **columna nueva `physical_state`**, no en el `status` legado: así el
enum legacy de MySQL (`DELIVERED/DISPATCHED/FAILED/SCHEDULED`) no se toca y la fila conserva su historia.
`physical_state` es **nullable** a propósito: las filas creadas por el flujo legacy nunca entraron a la
máquina, así que su estado físico se **deriva** en lectura (`DeliveryPhysicalState.fromLegacy`) en vez de
reescribirse ("no inventar históricos").

### Mapa de compatibilidad (v1)

- legacy → físico: `SCHEDULED|DISPATCHED → ASSIGNED`, `DELIVERED → COMPLETED`, `FAILED → FAILED`;
- físico → legacy (se aplica al materializar cada transición, para que quien lea `status` siga viendo algo
  coherente): `ASSIGNED|STARTED|ARRIVED|DELIVERING → DISPATCHED`, `COMPLETED → DELIVERED`,
  `FAILED|CANCELLED → FAILED`.

El `status` legado para v1, con `deliveries.status` siendo un `enum` en MySQL (V1/V2 no lo normalizaron),
queda **siempre dentro de los cuatro valores válidos**.

## Comandos y eventos

Comandos nuevos (el legacy `CompleteDeliveryCommand(deliveryId)` se deja intacto, por eso el cierre físico
es `CompletePhysicalDeliveryCommand`): `AssignDeliveryCommand`, `StartDeliveryCommand`,
`ArriveDeliveryCommand`, `CompletePhysicalDeliveryCommand(deliveryId, deliveredVolume)`,
`CancelDeliveryCommand(deliveryId, reason)`; se reutiliza el `FailDeliveryCommand(deliveryId, reason)`
existente.

Eventos en `fulfillment/api/events` (contrato público, T19-A), publicados por el **outbox transaccional**
existente (`EventPublicationRegistry`, `Propagation.MANDATORY`) con `eventType` versionado:

| evento | eventType | aggregateVersion |
| --- | --- | --- |
| `DeliveryAssigned` | `delivery.assigned.v1` | sí |
| `DeliveryStarted` | `delivery.started.v1` | sí |
| `DeliveryArrived` | `delivery.arrived.v1` | sí |
| `DeliveryCompleted` | `delivery.completed.v1` | sí |
| `DeliveryFailed` | `delivery.failed.v1` | sí |

T19-A dejó anotado que `aggregateVersion` se poblaría "una vez que un agregado lleve versión (p. ej.
`Delivery` en T14-A)": aquí `Delivery` ya lleva `version` y el evento lo registra. El `payloadJson` lo
produce el propio módulo (`toPayloadJson()` en cada record; sin serializador externo).

## Evidencia de `complete` (U11, 2026-09-22)

Evidencia = **volumen entregado numérico**, sin foto ni firma (no se agregó infraestructura de archivos).
Se permite variación: `deliveredVolume ≤ requestedVolume` es válido y **ambos valores se conservan**
(`deliveries.requested_volume` y `deliveries.delivered_volume`); **no** se exige coincidencia exacta. Un
volumen entregado ausente/no positivo, o superior al solicitado, se rechaza con **400**.

El **volumen solicitado es obligatorio** para cerrar (fix post-review, 2026-09-22): si
`findRequestedQuantity` no lo resuelve (orden inexistente o sin cantidad — flujo legacy/malformado), el
`complete` se rechaza con **422** (`BUSINESS_RULE_VIOLATION`) y no escribe nada. Antes la invariante se
saltaba cuando `requestedVolume` era `null` y se persistía `requested_volume=null` junto a un
`delivered_volume` real: exactamente el estado ambiguo que U11 quiso evitar ("ambos valores se conservan").
No se modela ningún caso legítimo de cierre sin volumen solicitado; si apareciera, sería una decisión
abierta, no un permiso implícito.

Para obtener el volumen solicitado sin que `fulfillment` alcance `ordering.domain..` (sería una violación
nueva de frontera), se agregó el método de lectura `findRequestedQuantity(orderId)` a la superficie pública
`ordering.application.queryservices.FuelOrderQueryService` (aditivo, sin exponer el agregado `FuelOrder`).

## Persistencia (migración aditiva `V17__delivery_physical_lifecycle.sql`)

- `deliveries`: `physical_state VARCHAR(30)`, `requested_volume`, `delivered_volume`, `started_at`,
  `arrived_at`, `delivering_at`, `version integer not null default 0` (bloqueo optimista `@Version`).
- Tabla nueva **`delivery_state_transitions`** (journal append-only): `delivery_id`, `from_state`,
  `to_state`, `aggregate_version`, `occurred_at`.

Cada comando aceptado, en una sola transacción: guarda el delivery (`saveAndFlush`, para que el conflicto
de versión aflore dentro de la TX y se traduzca a 409), **escribe la fila del journal** y **publica el
evento**. `aggregate_version` es la versión **resultante** del delivery tras la transición. Si algo falla,
no queda ni estado, ni journal, ni publicación — incluido un cierre rechazado por evidencia inválida: el
volumen se valida **antes** de tocar el agregado, así que un rechazo (o una carrera) no deja al delivery a
medio descargar.

## Contrato público

- `POST /api/v2/deliveries/{id}/start|arrive|complete|fail` (el contrato pedido por S14).
- Extra documentado, para que la máquina sea alcanzable y verificable: `POST .../{id}/assign` (materializa
  `ASSIGNED` + `DeliveryAssigned`, idempotente: reintentar no duplica el evento),
  `POST .../{id}/cancel` (terminal `CANCELLED`; se reporta por `DeliveryFailed`, que es el único evento de
  fallo definido en S14), `GET .../{id}` y `GET .../{id}/transitions` (journal observable).
- Autorización: el provider dueño del delivery (`iam.api.TenantAccess`); un delivery ajeno responde **404**
  (nunca una mutación cross-tenant). No se introdujeron roles nuevos (coherente con U07).
- **El pago queda desacoplado**: este servicio no lee ni escribe nada de `payment`, y ningún estado físico
  depende de `PAID`/`PENDING_PAYMENT`.

`complete` desde `ARRIVED` registra **dos** filas del journal (`ARRIVED → DELIVERING` y
`DELIVERING → COMPLETED`), porque el contrato de S14 no expone un paso separado de descarga y la tabla de
transiciones no permite saltar `DELIVERING`. Cada transición es observada por separado (fix post-review): el
discharge se **persiste** como un estado real (`DELIVERING` con su propio `saveAndFlush` y su propia
versión) antes de cerrar, así que cada fila lleva la versión resultante **de esa** transición y no la
versión post-completion repetida. Las dos escrituras viven en la misma transacción y la fila bloqueada por
la primera hace imposible una carrera sobre la segunda, así que un rechazo o una carrera siguen sin dejar
nada a medias.

## Tests

- `DeliveryPhysicalStateTest` (unit): tabla de transiciones, terminales y el mapa de compatibilidad en
  ambos sentidos (incluido `CANCELLED → FAILED`).
- `DeliveryLifecycleTest` (Spring + H2, `FuelOrderQueryService` mockeado): recorrido completo con journal
  (`ASSIGNED/STARTED/ARRIVED/DELIVERING/COMPLETED`) + 4 publicaciones en el outbox, verificando la versión
  **fila por fila** (el discharge desde `ARRIVED` lleva su propia versión, distinta de la post-completion);
  transición inválida → failure y **nada** escrito (ni journal ni evento); `complete` repetido → falla y
  **no suma** el volumen; volumen superior al solicitado → rechazado; volumen entregado ausente → rechazado;
  `requestedVolume` no resoluble → rechazado sin escribir estado/journal/evento; fallo desde un estado
  intermedio; cancelación explícita; `assign` idempotente (1 evento, 1 fila); una fila legacy se lee
  `ASSIGNED` sin reescribir su `physical_state`.
- `DeliveriesV2ControllerTest` (MockMvc): recorrido por HTTP (`start → arrive → complete` con
  `deliveredVolume=42.5`, `requestedVolume=100`), `409` por transición inválida, `400` por volumen excedido,
  `404` cross-tenant en `GET`, en las mutaciones y en el journal.

## Baseline de ArchUnit

**Sin cambios en el store congelado.** Las clases nuevas viven en `fulfillment..` y solo dependen de
paquetes permitidos (`iam.api`, `ordering.application.queryservices`, `shared..`, Spring/Jakarta). Ni
`delivery` ni `fleet` están en `BUSINESS_MODULES` (ver T12-A A4), así que tampoco aplican esas reglas.

## Asunciones abiertas

- **A1 — `delivery` sigue dentro de `fulfillment`.** El roadmap apunta a un módulo `delivery`, pero
  moverlo cambiaría las firmas congeladas de `DeliveryCommandServiceImpl`/`DeliveriesController`; la
  extracción se hará cuando T14-B/T15 hayan retirado esos acoplamientos.
- **A2 — sin `delivering` propio en el contrato.** El paso `DELIVERING` se registra implícitamente al
  cerrar desde `ARRIVED` (ver arriba). Si operaciones necesita observarlo en vivo, es un endpoint más.
- **A3 — cancelación reportada como `DeliveryFailed`.** S14 define un único evento de fallo; el payload
  distingue `terminalState=FAILED|CANCELLED`. Si producto quiere `DeliveryCancelled`, es un `v1` nuevo.
- **A4 — el v1 no se re-enruta todavía.** `DeliveriesController` sigue usando el camino legacy
  (`DeliveryCommandServiceImpl`), que no escribe `physical_state`; el mapa de compatibilidad lo cubre. T14-B
  es el dueño de esa adaptación (sin mezclar pago/nivel).
- **A5 — `assign` y `cancel` son endpoints extra** respecto al contrato listado en S14, agregados para que
  `ASSIGNED` y la cancelación sean alcanzables y testeables de punta a punta.
- **A6 — actores finos por transición** (p. ej. conductor vs proveedor) no se modelan: hoy autoriza el
  provider dueño. La identidad de conductor no existe como sujeto autenticado (coherente con U07).
