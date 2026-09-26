# T18-B — ACK, incidentes y límite de hardware

## Contrato

`POST /api/v2/deliveries/{deliveryId}/valve-observations` acepta `{ "state": "OPEN|CLOSED", "observedAt": "...", "commandId": "..." }`. La identidad se resuelve desde JWT y la asignación vigente; no se aceptan tenant ni conductor del body.

Un `OPEN` con comando `PENDING`, vigente y de esa entrega cambia el comando a `ACKED` y publica `valve.state.observed.v1` en la misma transacción. Un replay del mismo ACK es `200` sin un segundo evento. Si falta `commandId`, un único comando pendiente vigente se usa para reconciliar un ACK perdido. Comandos expirados se guardan como `EXPIRED` al conciliar. Cualquier otro `OPEN` se registra como `SPONTANEOUS_OPEN`, publica `safety.incident.detected.v1` y responde `202`. `CLOSED` publica solo `valve.state.observed.v1` y responde `200`.

`safety_incidents` es append-only: su repositorio expone solo `save` y lectura. Migración `V29__safety_incidents.sql`. Validación MySQL: pendiente, manual por el usuario.

## Límite físico

Según W6, `ValveStateObserved` lo emite el driver app. La operación y el ACK se modelan con eventos dentro del backend, sin driver de red ni firmware. No existe banco físico; la certificación de hardware queda fuera de alcance y satisface el DoD como límite explícito.

## Verificación

`ValveObservationsControllerTest` usa MockMvc para ACK, replay, expiración, apertura espontánea, conciliación de ACK perdido, autorización de conductor y aislamiento de comandos entre entregas.
