# T16-A — Contrato de evidencia de transporte (S16)

Parent spec: **S16 — Asociar tracking y carga con entrega** (`docs/PRIMEFUEL_MIGRATION_ROADMAP.md`,
líneas ~582-600). Contexto obligatorio y precondición del rediseño:
`docs/api-ledger/W6-REDESIGN-transport-evidence.md` (aprobado 2026-09-23).

Dependencias ya hechas: **T12-B** (`fleet.api.EligibilityQuery`), **T13-B** (`FleetReservation` con
`driverId`), **T14-B** (lifecycle físico del delivery; la asignación `driverId`/`providerId` vive en el
`Delivery`).

> **Alcance:** T16-A es el **contrato de ingestión** de evidencia de transporte y la proyección que
> mantiene. No incluye endpoint de consulta (eso es **T16-B**) ni `ValveStateObserved` (eso es **T18**).

---

## 0. Recordatorio del rediseño W6 (por qué no hay IoT)

`equipment.devicebinding` y `telemetry` (S07/S08) quedan **built-but-frozen**: nunca se tocaron en este
ticket y no se referencian desde `tracking`. La evidencia de transporte la reporta la **app del conductor**
—un principal v2 normal (`iam.api`, login con bearer y `MembershipAccess`)— no un sensor con credencial
propia. **Cero infraestructura IoT nueva**, sin protocolo binario, sin credencial de dispositivo.

Consecuencia asumida: la posición es tan confiable como el teléfono del conductor (puede mentir, apagar GPS
o quedarse sin señal). No es un downgrade del diseño; es la evidencia real disponible, tal como S18 ya asumía
"certificar hardware inexistente" fuera de alcance.

---

## 1. Qué se construyó

### 1.1 Módulo nuevo `tracking` (mismo patrón que `fleet`)

Agregado/proyección propio, con `tracking.api` como **única seam pública**. Nada fuera del módulo puede
tener una proyección de tracking ni una fila de evidencia cruda: `tracking.domain`/`infrastructure` no se
exponen.

```
tracking/
├── api/
│   ├── TransportEvidenceRecorder.java        (seam de escritura, única superficie pública)
│   └── events/
│       ├── DeliveryTelemetryReceived.java    (evento de posición; delivery.telemetry.received.v1)
│       ├── TransportEventJson.java           (render de payload, package-private)
│       └── package-info.java                 (forma de ValveStateObserved RESERVADA para T18)
├── domain/
│   ├── model/aggregates/DeliveryTracking.java        (proyección: latest confirmado)
│   ├── model/entities/TransportEvidenceSample.java   (evidencia cruda, append-only)
│   ├── model/commands/RecordPositionEvidenceCommand.java
│   ├── model/commands/RecordLoadEvidenceCommand.java
│   ├── model/valueobjects/GeoPosition.java
│   ├── model/valueobjects/LoadMilestone.java
│   ├── model/valueobjects/TransportEvidenceKind.java
│   └── repositories/{DeliveryTrackingRepository,TransportEvidenceSampleRepository}.java
├── infrastructure/
│   ├── services/TransportEvidenceRecorderImpl.java   (impl de `tracking.api`)
│   └── persistence/jpa/{entities,repositories,assemblers,adapters}/...
└── interfaces/rest/
    ├── TransportEvidenceController.java
    └── resources/{TransportEvidenceResource,TransportEvidenceAckResource}.java
```

**Lectura del delivery (seam nuevo, sin tocar el pasado):** `fulfillment.api.DeliveryTrackingLookup`
+ `fulfillment.infrastructure.services.DeliveryTrackingLookupImpl`. Es un **read seam** nuevo (no se
modificó ningún archivo existente de `fulfillment`): expone el `driverId`/`providerId` de la asignación que
ya dejaron S14/S15. `tracking` nunca importa `fulfillment.domain`/`infrastructure`.

### 1.2 Endpoint

`POST /api/v2/deliveries/{deliveryId}/transport-evidence`

Cuerpo — **o** posición, **o** hito de carga (discriminado por `type`, recursos `TransportEvidenceResource`):

```jsonc
// type = POSITION
{ "type": "POSITION", "latitude": 10.5, "longitude": -66.9,
  "accuracyMeters": 8.0, "recordedAt": "2026-10-01T10:00:00Z" }

// type = LOAD (volumen opcional)
{ "type": "LOAD", "milestone": "LOADED", "volume": 120.0, "unit": "LITRE",
  "recordedAt": "2026-10-01T09:00:00Z" }
```

Respuesta `201` (`TransportEvidenceAckResource`): `evidenceId`, `deliveryId`, `kind`, `milestone`,
`latestAdvanced`, `recordedAt`.

**Respuestas:** `201` evidencia aceptada (incluida la tardía, con `latestAdvanced=false`); `400` cuerpo
inválido (type/milestone desconocido, coordenadas ausentes, volumen ≤ 0); `403` el caller no es el driver
asignado o la asignación cruza tenants; `404` el delivery (o su driver) no existe; `422` hito imposible
(p. ej. `UNLOADED` antes de `LOADED`).

### 1.3 Auth y tenancy (la regla del rediseño)

El caller debe ser **el driver asignado a esa entrega específica**. La resolución es server-side, en el
controller, y se apoya en las seams públicas:

1. `iam.api.MembershipAccess.currentUserId()` — la identidad del principal (sin `userId` → `403`).
2. `fulfillment.api.DeliveryTrackingLookup.findAssignedDelivery(deliveryId)` — `providerId` y `driverId`
   de la asignación (ausente → `404`).
3. `fleet.api.FleetCatalog.findDriver(driverId)` — `userId` del driver y su tenant (ausente → `404`).
4. **Invariante de tenant:** `driver.providerId == delivery.providerId`; si no → `403` (asignación
   inconsistente).
5. **Identidad:** `driver.userId == caller.userId`; si no → `403` (no es el driver asignado).

> **Nunca** se lee un `driverId` del body: `TransportEvidenceResource` no tiene ese campo. Consistencia con
> el resto del código: los endpoints v2 de fulfillment no usan `@PreAuthorize` (el filtro bearer global +
> `TenantAccess`/`MembershipAccess` resuelven el principal); este ticket sigue ese mismo mecanismo, y usa
> `403`/`404` como el resto de los controllers v2.

---

## 2. Invariantes preservadas de S16 original

- **Mismo tenant entre driver y delivery** → paso 4 de §1.3 (`403`).
- **Una muestra tardía no reemplaza el último dato confiable** → vive en el agregado
  `DeliveryTracking.recordPosition(...)`: solo avanza si `recordedAt` es **estrictamente posterior** al
  `lastPositionAt`; en caso contrario devuelve `false`. La muestra **se guarda igual** como
  `TransportEvidenceSample` cruda (`latestAdvanced=false`), pero la proyección no retrocede.
  - Empate de timestamp (`== lastPositionAt`) se trata como tardío (first-wins determinista); documentado.
- **Binding ajeno (driver que no es el asignado) rechazado** → paso 5 de §1.3.

---

## 3. Eventos de dominio

- **`DeliveryTelemetryReceived`** (`tracking.api.events`, `delivery.telemetry.received.v1`): se publica en
  cada muestra de **posición** aceptada (tardía incluida), vía el outbox de T19-A
  (`EventPublicationRegistry`, `aggregateType="DeliveryTracking"`, `organizationId=providerId`). El payload
  lleva `latestAdvanced` para que el consumidor distinga la muestra que movió el latest de la tardía.
  - Se publica **dentro de la misma transacción** que la evidencia y la proyección (rollback no deja evento).
- **`ValveStateObserved`** — **NO implementado** (es T18). Se **reserva nombre y forma** en
  `tracking/api/events/package-info.java` para que T18 lo reutilice sin romper el contrato. T18 publicará
  desde este mismo módulo (driver como actor), sin IoT.
- Los hitos de carga (`LOADED`/`UNLOADED`) **no** publican evento en T16-A (no hay contrato de dominio
  pedido para ellos aquí; el hito queda en la proyección + evidencia cruda).

---

## 4. Persistencia (aditiva, `V21`)

`V21__transport_evidence.sql` — **aditiva**, sin tocar tablas existentes:

- `delivery_tracking` (proyección, único `uk_delivery_tracking_delivery(delivery_id)`): última posición
  confiable + `last_position_evidence_id`, estado de carga (`loaded`, `last_load_milestone`, `last_load_at`,
  `last_load_volume`, `last_load_unit`, `last_load_evidence_id`), `version` (lock optimista).
- `transport_evidence_samples` (evidencia cruda **append-only**): `kind` (POSITION/LOAD), coordenadas +
  `accuracy_meters`, `milestone`, `volume`/`unit`, `recorded_at` (reloj del dispositivo) **y**
  `received_at` (reloj de plataforma) separados, `latest_advanced`. Índice
  `ix_transport_evidence_samples_delivery(delivery_id, received_at)`.

Diseño intencional: **la proyección referencia la evidencia cruda** (`last_*_evidence_id`) y **no se
fabrica histórico** — la proyección es un "latest" consultable; el histórico se conserva en la tabla de
muestras. `loaded` usa `bit` y los instantes `datetime(6)`, coherentes con el resto del esquema (V16/V17).

---

## 5. Tests escritos (**como archivos, sin ejecutar**)

- `tracking/DeliveryTrackingTest` (unit, agregado): muestra más nueva avanza el latest, muestra tardía **no**
  retrocede, empate no avanza; `UNLOADED` antes de `LOADED` es inválido; volumen de carga se conserva.
- `tracking/TransportEvidenceControllerTest` (Spring + H2, MockMvc, de punta a punta):
  1. **evidencia válida entra** — `POSITION` → `201`, `latestAdvanced=true`, proyección y evidencia
     persistidas;
  2. **driver ajeno rechazado** — mismo tenant, otro driver → `403`, cero evidencia;
  3. **tenant cruzado rechazado** — delivery de tenant A asignado a un driver de tenant B → `403`;
  4. **muestra tardía** — llega después pero con `recordedAt` anterior → `201` `latestAdvanced=false`; el
     `lastPositionAt` de la proyección queda en el dato bueno y **ambas** muestras quedan como evidencia
     cruda;
  5. **hito de carga válido/inválido** — `LOADED`+`UNLOADED` válidos (`201`), milestone desconocido y
     volumen ≤ 0 → `400`, `UNLOADED` sin `LOADED` → `422`.

Ningún test fue ejecutado en esta máquina (ver §7).

---

## 6. Fronteras y decisiones de arquitectura

- **`tracking` no se agregó a la lista `BUSINESS_MODULES` de `ModuleBoundaryRulesTest`.** Es el mismo
  tratamiento que recibieron `fleet`/`supply`/`replenishment`/`telemetry` al sumarse (no se tocó la lista
  congelada en T11+). El módulo **sí** respeta la frontera por diseño: depende sólo de `*.api` de otros
  módulos, `shared` y su propio dominio. Agregarlo a la lista de ArchUnit (y regenerar el baseline
  congelado) queda como higiene de un ticket futuro; no se hizo aquí para no introducir un riesgo de build
  no verificable (§7).
- **`DeliveryTrackingLookup` es un seam nuevo, no una edición de `DeliveryAssignments`/`DeliveryIntegration`.**
  Se prefirió un read seam dedicado antes que sobrecargar la seam de escritura de asignación.
- **OpenAPI / ledger v1 intactos.** La ruta nueva es `v2`; `ApiLedgerSelfCheckTest` y `OpenApiSnapshotTest`
  cuentan **sólo** `/api/v1/**` (77), por lo que no cambian. `OpenApiSnapshotTest` reescribe el snapshot en
  cada corrida (incluirá la ruta v2 automáticamente); no requiere edición manual.
- **Lock optimista** en la proyección (`@Version`, `saveAndFlush`): un ping concurrente para el mismo
  delivery se detecta y responde `409` (`DELIVERYTRACKING_CONFLICT`), reproduciendo el patrón de
  `DeliveryLifecycleServiceImpl`. La serialización fuerte por fila (si hiciera falta bajo carga real alta)
  es endurecimiento de T16-B.

---

## 7. Estado del build

**Build no verificado en esta máquina — pendiente de verificación por el usuario.**

Regla dura aplicada: sólo se escribieron archivos de código, tests (como archivos) y la migración Flyway
`V21`. **No** se corrió `./mvnw test`, **no** se compiló, **no** se levantó la app, **no** se hizo commit.
Cada archivo está pensado para existir sin haber sido corrido; falta que el usuario verifique compilación,
suite y (si aplica) `ddl-auto=validate` sobre MySQL 8.0.46.

Riesgos residuales a revisar por el usuario: (a) compilación de los `switch`/text blocks de los tests;
(b) `validate` de Hibernate contra `V21` en MySQL (tipos `bit`/`datetime(6)`); (c) comportamiento real de la
seam `DeliveryTrackingLookup` en el contexto completo.

---

## 8. Asunción abierta **U10/U18** (retención de GPS/PII) — no bloquea T16-A

El **Definition of Done de S16 completo** (no el de T16-A) exige, según la spec original:
*"reglas comunes + U10/U18 sobre retención registradas"*.

- **U10/U18 — política de retención de GPS / datos personales de transporte: sin decisión de negocio.**
  Hoy T16-A persiste coordenadas crudas (`transport_evidence_samples`) y una proyección de "latest" **sin
  política de retención, anonimización ni borrado**. El endpoint respeta el scope de tenancy
  (`Security/tenancy impact: GPS y datos de transporte solo por scope permitido` se cumple a nivel de
  acceso), pero **la retención temporal no está definida**.
- **Dueño de la resolución:** **T16-B** debe resolver U10/U18 **con el usuario** antes de cerrar S16 (ventana
  de retención, borrado/anonimización, y qué se conserva como evidencia legal vs. qué es dato personal).
- **No bloquea T16-A:** el contrato de ingestión y la proyección funcionan sin esa política; es una decisión
  de negocio pendiente, documentada aquí como asunción abierta y heredada por T16-B.

---

## 9. Fuera de alcance de T16-A (para no confundir con la DoD de S16)

- **T16-B** — proyección **consultable** por REST (consulta v2 de seguimiento), timeline y la resolución de
  U10/U18.
- **T17-A/B** — geocerca y decisión de safety (consumen la proyección de posición).
- **T18-A/B** — `ValveStateObserved`, comando/outbox/ACK de válvula (**detection-only**, sin hardware).
- **T21-A/B** — journal/timeline de negocio.

---

## Addendum (2026-09-23, hallazgo de la auditoría de T17-A) — cota de reloj

`TransportEvidenceRecorderImpl` **rechaza (400 `validationError`)** una muestra (`POSITION` o `LOAD`) cuyo
`recordedAt` sea posterior a `now + 2 min` (skew tolerado). Sin cota superior, un timestamp futuro (p. ej.
2030) quedaba como “latest” para siempre —toda posición real posterior sería “tardía”— y `safety` jamás lo vería
como `STALE`. El skew de 2 min es el mismo que usa `GeofenceDecisionEvaluator` (`MAX_CLOCK_SKEW`); están
duplicados a propósito (módulos separados, sin importar internals). Los tests de `tracking` que fijaban
`recordedAt` en octubre de 2026 con reloj real pasaron a septiembre de 2026 (pasado) para no caer en el rechazo.
Build no verificado.
