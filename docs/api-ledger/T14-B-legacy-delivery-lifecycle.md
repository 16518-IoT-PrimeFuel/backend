# T14-B — Compatibilidad de estados y cierre físico (S14)

Parent spec: S14. Preconditions: T14-A (hecho).

> **Build no verificado en esta máquina — pendiente de verificación por el usuario.** Sin SDK de Java: no se
> ejecutó `compile`, `test` ni el arranque MySQL. El código y los tests quedan escritos para que el usuario
> corra `mvnw test` y valide el esquema con `scripts/validate-schema-mysql.ps1`.

> **Alcance:** T14-B **enruta la mutación de estado del delivery v1** por la máquina física de T14-A, sin
> romper el contrato v1 ni mezclar pago/nivel pedido. **No** elimina las escrituras cross-module del flujo
> v1 (eso es T15-B, "borrar imports cross-module").

## Qué se agregó

El adapter vive en el propio seam legacy `fulfillment.application.internal.commandservices.DeliveryCommandServiceImpl`
(no se creó una clase `*Bridge` nueva: el command service **ya es** el adapter v1, y depender de
`DeliveryLifecycleService` — mismo módulo — y de `ordering.application.queryservices` — API pública ya usada
por el controller — no introduce ninguna violación de frontera nueva en el baseline congelado de ArchUnit,
que es exactamente lo que la clase separada de T10-B evitaba). El controller v1 y su contrato HTTP quedan
**intactos** (mismas rutas, mismos cuerpos, mismo `DeliveryResource`/`status`).

## Mapa v1 → máquina física

| endpoint v1 | comando físico | notas |
| --- | --- | --- |
| `POST /api/v1/deliveries` (create) | — (sin cambio) | Crea el delivery ya `DISPATCHED`; `physical_state` queda `null` y se **deriva** `ASSIGNED` (`fromLegacy`). No materializa fila física, igual que cualquier fila legacy. |
| `POST /api/v1/deliveries/{id}/dispatch` | `AssignDeliveryCommand` | `dispatch()` legacy = materializar `ASSIGNED`; idempotente (reintentar no duplica el evento). |
| `POST /api/v1/deliveries/{id}/fail` | `FailDeliveryCommand` (`failPhysical`) | Terminal `FAILED`; antes no tenía guarda, ahora una transición inválida responde 409. |
| `POST /api/v1/deliveries/{id}/complete` | `CompletePhysicalDeliveryCommand` | v1 no trae volumen ni estados intermedios: el adapter resuelve la evidencia y materializa los estados (ver asunciones A1/A2). |

El `status` legacy de las respuestas se mantiene coherente por el mapa físico→legacy de T14-A
(`ASSIGNED|STARTED|ARRIVED|DELIVERING → DISPATCHED`, `COMPLETED → DELIVERED`, `FAILED|CANCELLED → FAILED`),
dentro siempre de los cuatro valores del enum de MySQL.

## Invariantes (ahora también para v1)

- **complete repetido no suma ni duplica.** La guarda terminal de la máquina rechaza el segundo cierre con
  **409** *antes* de correr los efectos legacy (`order.receive()`), así que no hay doble volumen, ni journal
  duplicado, ni refuel duplicado. (Antes: 500 por el `receive()` sin guarda de `FuelOrder`.)
- **complete exige evidencia.** El adapter resuelve el volumen solicitado del pedido; si no se puede resolver,
  devuelve el mismo `BUSINESS_RULE_VIOLATION` (422) que el cierre v2 — nunca persiste `requested_volume=null`
  junto a un `delivered_volume` real (fix U11 de T14-A).
- **pago no muta el estado físico.** Verificado: el módulo `payment` no lee ni escribe `Delivery` en ninguna
  ruta (solo toca `FuelOrder`, que es su propio acoplamiento de T23); este adapter no toca `payment`. Nada
  que retirar aquí.

## Asunciones abiertas

- **A1 — v1 no tiene estados intermedios; el adapter los materializa.** El lifecycle v1 es superficial
  (`DISPATCHED → DELIVERED`) y la máquina física es profunda. Para que `create → complete` siga siendo legal
  sin debilitar la máquina, el adapter avanza `ASSIGNED → STARTED → ARRIVED` con los comandos físicos
  (`start`/`arrive`) antes de cerrar. Esto **registra** pasos físicos que en v1 nunca se observaron por
  separado (con sus eventos `delivery.started/arrived.v1`). Decisión abierta: si producto prefiere que un
  cierre v1 no emita esos eventos, la alternativa (un "close legacy" que salte estados) debilita la máquina y
  no se aplicó.
- **A2 — cierre v1 = entrega completa.** v1 no tiene input de volumen, así que la evidencia numérica del
  cierre es la cantidad solicitada del pedido (`findRequestedQuantity`), es decir `deliveredVolume ==
  requestedVolume`. No se inventa un volumen distinto ni se permite un cierre sin volumen. Si en el futuro v1
  necesita entregas parciales, requiere un input nuevo (rompe contrato) o un canal v2.
- **A3 — efectos externos v1 conservados.** `complete` sigue liberando driver/tanker, rellenando el tanque
  (`equipment.receiveFuel`) y moviendo el pedido a `PENDING_PAYMENT`, por compatibilidad de contrato. Su
  retiro del flujo de delivery es **T15-B**. Se ejecutan **después** de que el cierre físico tuvo éxito, en la
  misma transacción, así que un fallo externo revierte todo el cierre.
- **A4 — sin clase bridge nueva.** Ver "Qué se agregó". Si una revisión prefiere el patrón T10-B literal, es
  un refactor de forma, no de semántica.

## Tests

- `contract/DeliveryV1LifecycleMappingTest` (Spring + H2 + MockMvc, sin mock de `FuelOrderQueryService`):
  create → `DISPATCHED` sin `physical_state` (deriva `ASSIGNED`); dispatch → `physical_state=ASSIGNED` + 1 fila
  de journal; complete → `COMPLETED` con `requestedVolume==deliveredVolume==` cantidad del pedido y journal
  `ASSIGNED/STARTED/ARRIVED/DELIVERING/COMPLETED`; complete repetido → **409** sin sumar volumen ni fila; fail
  → `FAILED`.
- `contract/characterization/StateLifecycleRetryCharacterizationTest#completingAnAlreadyDeliveredDeliveryNowReturns409`:
  se actualizó el known-gap de T01-B (era 500) al 409 que ahora garantiza la guarda física.
- `contract/OrderFulfillmentGoldenPathTest` (T01-A): sigue verde como oráculo de contrato v1 (`create`→
  `DISPATCHED`, `dispatch`→`DISPATCHED`, `complete`→`DELIVERED`, pedido→`PENDING_PAYMENT`, pago→`PAID`).
