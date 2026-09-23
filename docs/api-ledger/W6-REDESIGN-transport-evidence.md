# W6 — Rediseño: evidencia de transporte sin `devicebinding`/`telemetry` (S07/S08 frozen)

> **Aprobado (2026-09-23).** Driver-app-reportado en vez de sensor-IoT es el alcance real del producto.
> Desbloquea W6 (T16-A/B, T17-A/B, T18-A/B, T21-A/B).

Desbloquea W6 (S16→S17→S18→S21, tickets T16-A/B, T17-A/B, T18-A/B, T21-A/B) reemplazando la fuente de
evidencia que el roadmap original asumía.

## Por qué hace falta

`equipment.devicebinding` (credencial de sensor, rotación, `DeviceCredential`/`DeviceBinding`) está **atada a
un tanque estacionario** (`ActiveBindingImpl`, `TankReadingService`) — no a un vehículo en tránsito. Es
correcto que quede congelada: nunca fue pensada para tracking móvil. Usarla para S16 sería forzar un
acoplamiento nuevo sobre un módulo que el negocio ya decidió no seguir desarrollando.

## Qué cambia

**Antes (roadmap original):** S16 recibe evidencia de un dispositivo IoT autenticado vía `devicebinding`,
igual que un sensor de tanque.

**Ahora (rediseño):** la evidencia de transporte la reporta la **app del conductor**, autenticada como
cualquier otro endpoint v2 — el conductor ya es un principal de `iam.api` (login normal, `MembershipAccess`),
no un dispositivo con credencial propia. Cero infraestructura IoT nueva.

- **Fuente:** `POST /api/v2/deliveries/{deliveryId}/transport-evidence` — el driver asignado a la entrega
  (`FleetReservation.driverId`, ya resuelto en S13/S15) reporta posición + hitos de carga. Auth: el mismo
  `@PreAuthorize` que ya usan los endpoints v2 de fulfillment, verificando que el caller es el driver de esa
  entrega (no cualquier driver del tenant).
- **Granularidad:** pings periódicos (posición, timestamp, accuracy) desde el propio teléfono del conductor
  (GPS nativo del OS, sin hardware dedicado) + eventos discretos de carga (`LOADED`, `UNLOADED`, con volumen
  opcional). No hay protocolo binario ni ACK de bajo nivel — es un POST autenticado como cualquier otro.
  Correlación con `Delivery` reemplaza la correlación con `Tank` que tenía S16 original.
  - **Consecuencia real:** la posición es tan confiable como el teléfono del conductor (puede mentir, apagar
    GPS, o no tener señal). Esto **no es un downgrade del diseño, es honestidad sobre la evidencia real
    disponible** — la spec original de S18 (ACK de válvula física) ya asumía "certificar hardware inexistente"
    como fuera de alcance; este rediseño extiende ese mismo criterio a S16/S17.
- **Invariantes que se mantienen de S16 original:** device/tanker/delivery de mismo tenant; una lectura
  tardía no reemplaza el último dato confiable; binding ajeno (driver que no es el asignado) se rechaza.
- **Eventos de dominio:** se conservan `DeliveryTelemetryReceived` y `ValveStateObserved` (mismos nombres,
  misma forma), solo cambia el emisor — antes lo publicaba un consumer de `telemetry`, ahora lo publica el
  comando REST del driver app directamente.

## Impacto en cada ticket de W6

- **S16/T16-A/T16-B (tracking):** sin cambio de objetivo/contrato de consulta v2; cambia solo la fuente de
  ingestión (driver app en vez de sensor). Dependencias pasan de S07/S08 a **S12, S13, S14** (driver/tanker
  elegibles y delivery ya existen).
- **S17/T17-A/T17-B (geocerca):** sin cambio — sigue consumiendo la proyección de posición de S16, no le
  importa de dónde vino.
- **S18/T18-A/T18-B (válvula):** el ítem más afectado. Sin hardware real de válvula (ya lo dice la spec
  original: "certificar hardware inexistente" es out of scope), y sin `devicebinding` para IoT, **T18-A/T18-B
  deben declararse explícitamente `detection-only`**: se modela el comando/outbox/ACK como si existiera un
  gateway, pero sin dispositivo físico que lo ejecute — igual que ya contempla la Definition of Done original
  ("prueba física o alcance detection-only"). No se inventa hardware ni se pretende ACK real.
- **S21/T21-A/T21-B (journal):** sin cambio — consume los mismos eventos, ahora con el driver como actor en
  vez de un `deviceId` de sensor.

## Qué NO cambia

- No se toca `equipment.devicebinding` ni `telemetry` (siguen built-but-frozen, tal como decidiste).
- No se agrega infraestructura IoT nueva, ni protocolo binario, ni credenciales de dispositivo.
- El contrato v1 no se modifica (S16-S21 son aditivos v2, como el resto del roadmap).

## Antes de mandarlo a Command Code

Confirmame si este reemplazo (driver-app-reportado en vez de sensor-IoT) es aceptable como alcance real del
producto, o si preferís mantener W6 completamente en pausa hasta tener hardware de tracking real. Si lo
aprobás, el siguiente paso es un prompt para T16-A con este rediseño como contexto.
