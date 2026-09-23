# T16-B — Proyección y consulta de seguimiento (S16, cierra S16)

Parent spec: **S16 — Asociar tracking y carga con entrega** (`docs/PRIMEFUEL_MIGRATION_ROADMAP.md`,
líneas ~582-600 y el callout **U10/U18** inmediatamente después de la Definition of Done). Contexto
obligatorio: `docs/api-ledger/T16-A-transport-evidence-contract.md` (contrato de ingestión, módulo
`tracking`, agregado `DeliveryTracking`, entidad `TransportEvidenceSample`).

Dependencia: **T16-A** (ya hecho).

> **Alcance:** T16-B añade la **consulta** de seguimiento sobre `tracking.api`, prueba el **rebuild**
> determinístico de la proyección desde la evidencia cruda, y **registra** la decisión **U10/U18** que la
> Definition of Done de S16 exigía. No implementa `ValveStateObserved` (T18) ni el journal (T21). No toca
> `equipment.devicebinding`/`telemetry` (siguen congelados).

---

## 1. Endpoints de consulta (solo lectura, sobre `tracking.api`)

Ambos cuelgan de `/api/v2/deliveries` y son servidos por `DeliveryTrackingQueryController`, que usa la
seam pública nueva **`tracking.api.DeliveryTrackingQuery`** (`latest` + `samples`). Nada fuera de
`tracking` lee la proyección ni las muestras.

- **`GET /api/v2/deliveries/{deliveryId}/tracking`** → el **latest** (`DeliveryTrackingResource`):
  posición confiable (`lastLatitude`, `lastLongitude`, `lastAccuracyMeters`, `lastPositionAt`,
  `lastPositionEvidenceId`), estado de carga (`loaded`, `lastLoadMilestone`, `lastLoadAt`,
  `lastLoadVolume`, `lastLoadUnit`, `lastLoadEvidenceId`), `driverId`/`providerId` y `version`.
  Sin tracking aún → `404`.
- **`GET /api/v2/deliveries/{deliveryId}/tracking/samples`** → el **track/histórico**
  (`List<TrackingSampleResource>`): una fila por muestra cruda. Vacío → `200` con `[]`.

### 1.1 Auth y tenancy (mismo criterio server-side que T16-A)

Nunca se confía un id del body (ni hay body): el `deliveryId` de la ruta se resuelve contra las seams
públicas y el principal:

1. `fulfillment.api.DeliveryTrackingLookup.findAssignedDelivery(deliveryId)` → `providerId`/`driverId`
   (ausente → `404`).
2. `fleet.api.FleetCatalog.findDriver(driverId)` → `userId` y tenant del driver (ausente → `404`).
3. **Permitido** si:
   - **proveedor dueño** — `iam.api.TenantAccess.currentProviderId() == delivery.providerId`; **o**
   - **driver asignado** — `iam.api.MembershipAccess.currentUserId() == driver.userId` **y** la asignación
     es consistente en tenant (`driver.providerId == delivery.providerId`).
4. Si no, `403`.

Diferencia deliberada con T16-A (que es **solo del driver**): la **consulta** la ven el driver asignado o
el proveedor dueño — ambos roles legítimos para ver el tracking. La consistencia de tenant impide que un
driver de otro tenant (asignación cruzada) lea por la vía de identidad.

---

## 2. Ordenamiento y calificación de muestras (jitter)

`GET .../tracking/samples` ordena por **`recordedAt`** (reloj del dispositivo) — con `receivedAt` e **
`latestAdvanced`** visibles en cada fila, para que quien consume distinga:

- una **muestra tardía** (llegó después, `latestAdvanced=false`, no movió el latest), de
- una que **sí movió** el latest (`latestAdvanced=true`).

El orden se hace en la capa de persistencia:
`TransportEvidenceSamplePersistenceRepository.findByDeliveryIdOrderByRecordedAtAscReceivedAtAscIdAsc`
(tie-break determinista: `receivedAt`, luego `id`). Se añadió
`TransportEvidenceSampleRepository.findByDeliveryIdOrderedByRecordedAt` a la seam de repositorio;
`findByDeliveryId` (orden de recepción) se conserva.

Consecuencia útil: una muestra tardía aparece **en su lugar cronológico**, no al final donde llegó.

---

## 3. Rebuild determinístico de la proyección

`DeliveryTracking` es un valor **derivado** de `transport_evidence_samples`. La seam nueva
**`tracking.api.TrackingProjectionRebuilder.rebuild(deliveryId)`** (impl
`TrackingProjectionRebuilderImpl`, `@Transactional`) reconstruye la proyección:

1. lee las muestras **en orden `recordedAt`**;
2. `reset()`ea el agregado (nuevo método en `DeliveryTracking`; conserva `id`, `deliveryId`, `providerId`,
   `driverId` y `version`);
3. **reproduce** cada muestra por el **mismo** agregado que usa el grabador en vivo
   (`recordPosition`/`recordLoad`), enlazando `last_*_evidence_id`;
4. persiste y devuelve el snapshot.

Determinismo: procesar la **misma evidencia** da **siempre la misma proyección**, sin importar el orden de
llegada (jitter) — sólo cambia cuándo una muestra se vuelve visible. El `driverId` de la proyección se
toma de la muestra **más recientemente recibida**, replicando exactamente lo que mantiene el grabador en
vivo (que refresca la asignación en cada muestra), por lo que un rebuild coincide campo a campo con el
estado vivo.

- **No hay endpoint de rebuild operable** (no se pide): es una garantía **probada por test**
  (`TrackingProjectionRebuildTest`), no un comando de operador.
- ~~⚠️ Supuesto: el replay asume muestras producidas bajo el invariante del agregado (`UNLOADED` no precede
  a `LOADED`). Evidencia corrupta que lo viole haría fallar el rebuild en vez de producir una proyección
  silenciosamente equivocada.~~ **Superado por el fix post-review 2/2 de abajo** — el rebuild ya no falla
  ante esa evidencia, la trata como muestra no-avanzada.

> **Fix post-review 1/2 (2026-09-23):** `recordLoad` ahora respeta la misma invariante de tardía que
> `recordPosition`; el rebuild es determinístico también para hitos de carga fuera de orden.
>
> El review de dos ejes detectó que `DeliveryTracking.recordLoad` **no** tenía la guarda de "muestra tardía
> no retrocede el latest" que sí tenía `recordPosition`, lo que rompía la afirmación de determinismo: con dos
> hitos `LOAD` fuera de orden cronológico, el estado en vivo (orden de llegada) y el reconstruido (orden
> `recordedAt`) podían divergir. Corrección aplicada:
> - `recordLoad` pasa a devolver `boolean` (= `recordPosition`): solo avanza el latest si `recordedAt` es
>   **estrictamente posterior** a `lastLoadAt`; empate o anterior → `false` y **no muta ningún campo**
>   (`loaded`, `lastLoadMilestone`, `lastLoadAt`, volumen/unidad).
> - La validación `UNLOADED solo después de LOADED` **solo** aplica a la muestra que va a avanzar, comparada
>   contra el `loaded` actual; una muestra tardía **no se valida** (no lanza `IllegalStateException`), se
>   guarda igual como evidencia cruda.
> - `TransportEvidenceRecorderImpl.recordLoad` usa el `boolean` para poblar `EvidenceAck.latestAdvanced`
>   (antes siempre `true`, lo cual era incorrecto); la muestra cruda se guarda siempre y solo se enlaza
>   (`linkLoadEvidence`) cuando de verdad avanzó. `TransportEvidenceSample.load(...)` ahora recibe el flag
>   `latestAdvanced` real.
> - `TrackingProjectionRebuilderImpl.replay` (case `LOAD`) usa el `boolean` para enlazar la evidencia solo
>   cuando avanzó.
>
> Tests que lo fijan: `DeliveryTrackingTest` (un `LOAD` tardío no muta y devuelve `false`; un `LOAD` tardío
> que rompería la secuencia no lanza excepción) y `TrackingProjectionRebuildTest` (jitter de hitos `LOAD`
> fuera de orden: el rebuild reconstruye lo mismo que el estado en vivo / que el orden cronológico correcto).

> **Fix post-review 2/2 (2026-09-23, aplicado por el orquestador directamente, no por Command Code):** el
> rebuild ya no propaga `IllegalStateException` por evidencia cruda con secuencia `LOAD` imposible.
>
> El fix 1/2 dejó un residuo: `TrackingProjectionRebuilderImpl.replay` reprocesa las muestras en orden
> estricto de `recordedAt`, así que casi todas "avanzan" durante el rebuild — lo que dispara la validación
> `UNLOADED`-sin-`LOADED` ahí, aunque esa misma muestra nunca la disparó en vivo (porque llegó tarde y el
> chequeo de tardía la filtra antes de validar la secuencia). Evidencia cruda válida en vivo (ej. un
> `UNLOADED` que llegó tarde, con `recordedAt` anterior al `LOADED` ya aplicado) podía hacer que el rebuild
> reventara con `IllegalStateException` al reprocesarla en orden cronológico, donde queda "primera" y sin
> `LOADED` previo.
> - `TrackingProjectionRebuilderImpl.replay` (case `LOAD`) ahora envuelve `tracking.recordLoad(...)` en
>   `try/catch(IllegalStateException)`; si la excepción ocurre, la muestra se trata como no-avanzada (no se
>   enlaza evidencia, no se detiene el rebuild) — mismo criterio que el resto del diseño: nunca perder
>   evidencia, nunca reventar por datos crudos inconsistentes.
> - No se tocó `DeliveryTracking.recordLoad` — el agregado ya está bien: valida la secuencia solo cuando la
>   muestra sí va a avanzar, que es el comportamiento correcto para el recorder en vivo.
>
> Test que lo fija: `TrackingProjectionRebuildTest.rebuildingSurvivesRawEvidenceWithAChronologicallyImpossibleLoadSequence`
> — un `LOADED@08:00` (avanza) seguido de un `UNLOADED@07:00` (tardío, no avanza, se guarda igual) reproduce
> exactamente el caso donde el rebuild, al reprocesar por `recordedAt`, encuentra el `UNLOADED` primero contra
> un estado recién reseteado (`loaded=false`) — antes del fix esto lanzaba, ahora el rebuild converge al mismo
> estado que el live sin excepción.

---

## 4. Decisión U10/U18 (retención de GPS/PII) — registrada

La DoD de S16 exige *"U10/U18 sobre retención registradas"*. **Decisión ya tomada (no resuelta por este
ticket); se documenta y se implementa lo mínimo coherente:**

### U10 — sin límite de retención

**`transport_evidence_samples` se conserva indefinidamente.** No se implementa ningún job de purga,
borrado por antigüedad, anonimización ni TTL. No se toca la tabla de T16-A. Queda **explícito** para que
nadie asuma una política de retención que no existe: a día de hoy **no hay vencimiento** de GPS ni de
datos de transporte. (Qué se conserva como evidencia legal vs. qué es dato personal queda, por decisión,
sin recorte temporal.)

### U18 — borrado/export **reservado, no operativo**

Se define la **forma y ruta** del contrato de borrado/export pero **no se expone activo**:

- `DELETE /api/v2/admin/deliveries/{deliveryId}/transport-evidence`
- `GET    /api/v2/admin/deliveries/{deliveryId}/transport-evidence/export`

`ReservedTransportEvidenceAdminController` responde **`501 Not Implemented`** con cuerpo
`ErrorResource{code:"NOT_IMPLEMENTED", message, details}` y **no toca ni expone evidencia**. Es deliberado
que **no** sea un `404` silencioso: el contrato **existe**, sólo que la operación aún no está habilitada.

**Por qué reservado:** la autorización real de estas operaciones requiere el rol de plataforma
**`ROLE_ADMIN`**, que **no existe** todavía (`Roles` sólo tiene `ROLE_BUYER`/`ROLE_PROVIDER`; ver T22-A/T23-A)
y cuya creación es **T24-PRE-ADMIN**, aún no construida. Mismo tratamiento que T16-A le dio a
`ValveStateObserved`: **se reserva el contrato, no se implementa la autorización real** hasta que el rol
exista.

> Con esto, la DoD de S16 ("reglas comunes + U10/U18 registradas") queda **completa**.

---

## 5. Tests escritos (**como archivos, sin ejecutar**)

- `tracking/DeliveryTrackingQueryControllerTest` (Spring + H2, MockMvc):
  1. **latest por driver asignado y por proveedor dueño** — ambos `200` y ven la posición;
  2. **histórico en orden de reloj con jitter** — se ingieren `10:10`, `10:00`, `10:05` (orden de llegada
     desordenado) y la lista sale `10:00, 10:05, 10:10` con `latestAdvanced` `false,false,true`; el latest
     es `10:10`;
  3. **tenant ajeno rechazado** — `403` en latest y en samples;
  4. **delivery sin tracking** — `404`;
  5. **contrato reservado** — `DELETE` y `GET /export` responden `501` con `code=NOT_IMPLEMENTED` y **sin**
     datos de evidencia;
  6. **estado de carga visible** en el latest (`loaded`, `lastLoadMilestone`, `lastLoadVolume`).
- `tracking/TrackingProjectionRebuildTest` (Spring + H2, seam directa):
  1. **rebuild determinístico con jitter** — tras ingerir `LOADED`, posiciones desordenadas y `UNLOADED`, el
     rebuild reproduce el latest **campo a campo** (ignorando `version`) y coincide con el estado vivo;
     un segundo rebuild da lo mismo;
  2. **rebuild con hitos LOAD fuera de orden** (T16-B fix) — un `UNLOADED` llega antes que el `LOADED` que
     lo precede cronológicamente; el rebuild reconstruye el mismo estado que el vivo **y** que los mismos
     hitos recibidos en orden cronológico correcto;
  3. **rebuild sin muestras** → `Optional.empty()`.
- `tracking/DeliveryTrackingTest` (unit, agregado; T16-B fix): un `LOAD` tardío no muta el estado y devuelve
  `false`; un `LOAD` tardío que rompería la secuencia (`UNLOADED` con `loaded=false`) no lanza excepción,
  simplemente no avanza.

Ningún test fue ejecutado en esta máquina (ver §7).

---

## 6. Fronteras y decisiones de arquitectura

- **Seams nuevas en `tracking.api`**: `DeliveryTrackingQuery` (lectura) y `TrackingProjectionRebuilder`
  (mantenimiento). Se mantiene `tracking.api` como única superficie pública del módulo.
- **`reset()`** se añadió al agregado `DeliveryTracking` (T16-A) — es lógica de dominio del rebuild.
- **`findByDeliveryIdOrderedByRecordedAt`** se añadió al repositorio de muestras y a su impl de
  persistencia; **no** se modificó el esquema: T16-B **no** trae migración Flyway (usa las tablas `V21` de
  T16-A tal cual).
- **Sin evento nuevo.** T16-B es consulta/mantenimiento: no publica eventos de dominio.
- **OpenAPI / ledger v1 intactos.** Rutas nuevas son `v2`; `ApiLedgerSelfCheckTest` y `OpenApiSnapshotTest`
  cuentan **sólo** `/api/v1/**` (77). El endpoint reservado `501` también es v2.
- **ArchUnit:** `tracking` sigue **fuera** de `BUSINESS_MODULES` (mismo tratamiento que
  `fleet`/`supply`/`replenishment`/`telemetry`); el módulo respeta la frontera por diseño (sólo `*.api`
  ajenas + `shared` + dominio propio). Registrarlo y regenerar el baseline queda como higiene futura, igual
  que en T16-A.

---

## 7. Estado del build

**Build no verificado en esta máquina — pendiente de verificación por el usuario.**

Regla dura aplicada: sólo se escribieron archivos de código y tests (como archivos) más este ledger. **No**
se corrió `./mvnw test`, **no** se compiló, **no** se levantó la app, **no** se hizo commit. **No** hay
migración nueva (`V21` es de T16-A y no se toca). Cada archivo está pensado para existir sin haber sido
corrido; falta que el usuario verifique compilación y suite.

Riesgos residuales a revisar por el usuario: (a) la derivación Spring Data
`findByDeliveryIdOrderByRecordedAtAscReceivedAtAscIdAsc` (nombre largo, debe resolverse en runtime);
(b) la comparación recursiva de AssertJ sobre los records de snapshot; (c) que el `501` reservado conviva
con el filtro bearer global (`anyRequest().authenticated()`).

---

## 8. Fuera de alcance de T16-B

- **T17-A/B** — geocerca y decisión de safety (consumen `DeliveryTrackingQuery.latest`).
- **T18-A/B** — `ValveStateObserved`, comando/outbox/ACK de válvula (**detection-only**).
- **T21-A/B** — journal/timeline de negocio.
- **T24-PRE-ADMIN** — rol de plataforma `ROLE_ADMIN`, que habilitaría (en un ticket posterior) la
  operación real detrás del contrato reservado de U18.
- **Purga/retención ejecutable (U10)** — no existe por decisión; si el negocio cambia de criterio, será un
  ticket propio.
