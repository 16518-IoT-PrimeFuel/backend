# T17-A — Modelo y validación de geocerca (S17)

Parent spec: **S17 — Introducir política geográfica de seguridad** (`docs/PRIMEFUEL_MIGRATION_ROADMAP.md`,
líneas ~614-632) y el callout **U09** inmediatamente después de la Definition of Done. Contexto obligatorio:
`docs/api-ledger/T16-B-tracking-query-and-retention.md` (de donde sale la posición: 
`tracking.api.DeliveryTrackingQuery.latest(deliveryId)`).

Dependencias ya hechas: **T14-B** (delivery físico), **T16-B** (`DeliveryTrackingQuery.latest`), **T06-B**
(tanques/lecturas; aquí solo como contexto de por qué el destino no expone coordenadas).

> **Alcance:** T17-A construye la **decisión pura y versionada** de geocerca (modelo, evaluación y
> persistencia append-only) y la administración v2 de la política. **No** la integra al lifecycle de
> `Delivery` (eso es **T17-B**), **no** emite comando físico de válvula (eso es **T18**), **no** escribe el
> journal de negocio (**T21**). No toca `equipment.devicebinding`/`telemetry`.

---

## 0. Módulo `safety` (decisión de naming)

El ticket sugería `delivery/safety/geofence`. Se creó un **módulo propio `safety`**
(`com.primefuel.fulltank.platform.safety`) siguiendo la convención del repo de un módulo por contexto
acotado (como `fleet`, `supply`, `tracking`), porque safety (S17 geocerca + S18 válvula) es un contexto
distinto de `fulfillment`. `safety` depende **solo** de `*.api` de otros módulos (`tracking.api`,
`fulfillment.api`, `iam.api`) + `shared` + su propio dominio; nunca de `.domain`/`.infrastructure` ajenos.

```
safety/
├── api/
│   ├── GeofencePolicies.java          (seam de escritura/lectura de políticas)
│   ├── GeofenceEvaluation.java        (seam de decisión)
│   └── events/{ValveAuthorized,ValveBlocked,SafetyEventJson}.java
├── domain/
│   ├── model/aggregates/GeofencePolicy.java
│   ├── model/entities/SafetyDecision.java
│   ├── model/valueobjects/{GeofenceDecision,GeofenceBlockReason,TrackedPosition}.java
│   ├── model/commands/CreateGeofencePolicyCommand.java
│   ├── domain/services/{GeofenceDecisionEvaluator,GeoDistance}.java
│   └── repositories/{GeofencePolicyRepository,SafetyDecisionRepository}.java
├── infrastructure/
│   ├── services/{GeofencePoliciesImpl,GeofenceEvaluationImpl}.java
│   └── persistence/jpa/{entities,repositories,assemblers,adapters}/...
└── interfaces/rest/GeofencePoliciesController.java + resources/...
```

---

## 1. `GeofencePolicy` — agregado versionado, círculo (U09)

Política de geocerca de un delivery: **centro + radio** (círculo, no polígono — U09). Campos:
`deliveryId`, `providerId`, `centerLatitude`, `centerLongitude`, `radiusMeters`, `policyVersion`.

- **Append-only / versionado:** una política **nunca se edita in-place**. Crear una política para un delivery
  produce la versión `n+1` y **la anterior se conserva**; la “vigente” es la de mayor `policyVersion`. El
  único `(delivery_id, policy_version)` (`V22`) impide duplicar versiones; una creación concurrente →
  `409` (`GEOFENCEPOLICY_CONFLICT`).
- **Coordenadas del destino en la propia política.** No se depende de que `equipment`/`Tank` expongan
  coordenadas (hoy no las tienen): S17 no se bloquea en eso. Si en el futuro el sitio aporta el centro, será
  otra fuente de la misma forma.
- Validación en el agregado: lat ∈ [-90,90], lon ∈ [-180,180], radio > 0; `deliveryId`/`providerId` requeridos.

---

## 2. `GeofenceDecision` — decisión pura (U09)

`GeofenceDecision` es **sealed** con dos resultados, espejo de los eventos del roadmap: `Authorized` y
`Blocked` (con `GeofenceBlockReason`). `GeofenceDecisionEvaluator` evalúa **(política, posición, `now`)** y
es **puro**: no lee tiempo absoluto (el `now` entra por parámetro), por lo que es determinista y testeable
sin Spring.

Reglas, en orden (U09):

0. **Timestamp futuro** — si `recordedAt` es posterior a `now + 2 min` (`MAX_CLOCK_SKEW`) →
   `Blocked(FUTURE_TIMESTAMP)`. Sin esto, una muestra con fecha muy futura quedaría como “latest” para siempre y
   jamás sería `STALE`. Es defensa en profundidad: el recorder de `tracking` ya rechaza (400) muestras
   fechadas más allá del mismo skew, pero datos previos o un cambio futuro no deben poder saltarse la regla.
1. **Freshness** — si `recordedAt` de la última posición (`DeliveryTrackingQuery.latest`) es **estrictamente
   anterior** a `now − 5 min` → `Blocked(STALE)`. Exactamente en el borde de 5 min todavía es fresco.
2. **Accuracy** — si `accuracyMeters` es **desconocido (null)** o `> 50` → `Blocked(INACCURATE)`. Fail-closed:
   la incertidumbre deniega (S17: “posición fiable; incertidumbre deniega”).
3. **Distancia** — distancia haversine (`GeoDistance.metersBetween`) entre la posición y el centro. El
   **círculo de incertidumbre debe quedar estrictamente dentro** del radio:
   `distance + accuracy >= radiusMeters` → `Blocked(OUTSIDE)`; sólo `distance + accuracy < radiusMeters` →
   `Authorized`. **El borde bloquea** (regla de borde explícita, fail-closed), por eso la desigualdad del
   autorizado es estricta.
4. Sin posición (pero con política) → `Blocked(NO_POSITION)` (fail-closed).
5. **Sin política** → `Blocked(NO_POLICY)` (decisión del usuario, 2026-09-23): una entrega sin geocerca
   configurada **deniega**, no “no aplica”. Se registra una fila en `safety_decisions` con `policy_id` y
   `policy_version` en `NULL` (por eso ambas columnas son nullable en `V22`) y se publica `ValveBlocked` con
   esos campos en `null`. Sólo una entrega inexistente falla (`NotFound`). T17-B debe tratar el resultado
   como bloqueo.

`Blocked` lleva `distanceMeters`/`accuracyMeters` (null cuando no hay posición) para que la decisión quede
explicada.

> **No es un actuador.** `Authorized`/`Blocked` son una decisión; abrir/cerrar la válvula (comando, outbox,
> ACK) es T18. Nada aquí previene un evento físico.

---

## 3. Persistencia (`V22`, aditiva)

`V22__geofence_policies_and_safety_decisions.sql` — **aditiva**, sin tocar tablas existentes:

- **`geofence_policies`** — versiones de política; único `uk_geofence_policies_delivery_version(delivery_id,
  policy_version)`.
- **`safety_decisions`** — **append-only**, una fila por decisión: `delivery_id`, `provider_id`, `policy_id`,
  `policy_version` (la política **aplicada**), `authorized`, `reason`, `tracking_evidence_id` (la **muestra de
  tracking** que originó la decisión), `observed_at` (reloj del dispositivo), `evaluated_at` (reloj de
  plataforma), `distance_meters`, `accuracy_meters`. Índice `(delivery_id, evaluated_at)`. Los repositorios
  solo exponen `save`/lectura: **nunca update/delete**.

---

## 4. Administración v2 restringida

`POST /api/v2/deliveries/{deliveryId}/geofence-policies` (`GeofencePoliciesController`) crea/versiona la
política. **Auth server-side, mismo criterio de tenant que el resto de v2:** `iam.api.TenantAccess`
(`currentProviderId`) debe ser el **proveedor dueño** del delivery, resuelto vía
`fulfillment.api.DeliveryTrackingLookup`; el `providerId` **nunca** viene del body. Delivery inexistente →
`404`; proveedor ajeno o sin identidad de proveedor → `403`; geometría inválida → `400`; versión concurrente →
`409`. `createPolicy` **no es `@Transactional`** a propósito: la violación del único por versión concurrente se
lanza dentro de la transacción propia del repositorio (`saveAndFlush`) y se captura sin dejar una TX externa
marcada rollback-only (lo que produciría `UnexpectedRollbackException`/500 en lugar del 409). **No** hay test de
concurrencia real todavía: el 409 queda **sin verificar**. **No** se expone endpoint de decisión: `GeofenceEvaluation` es una seam in-process que T17-B cableará
al lifecycle.

---

## 5. Eventos de dominio

`GeofenceEvaluationImpl` publica, vía el outbox de T19-A (`EventPublicationRegistry`, `@Transactional`),
`ValveAuthorized` (`safety.valve.authorized.v1`) o `ValveBlocked` (`safety.valve.blocked.v1`) según el
resultado — mismos **nombres** del roadmap (S17: “`ValveAuthorized` o `ValveBlocked`; no comando físico
aún”). El payload lleva `decisionId`, `deliveryId`, `policyId`, `policyVersion`, `trackingEvidenceId`,
distancia/accuracy y los instantes. **Sin comando físico**: T18 reutiliza estos eventos.

---

## 6. Reloj inyectado

`GeofenceEvaluationImpl` recibe un `java.time.Clock` por constructor (mismo patrón que
`TransportEvidenceRecorderImpl`) y usa `clock.instant()` como `now`; nunca `Instant.now()` inline. El
evaluador puro recibe ese `now` por parámetro. Así la **freshness** se prueba con tiempo fijo (bean `Clock`
`@Primary` en test), de forma determinista.

---

## 7. Tests

- `safety/GeofenceDecisionEvaluatorTest` (unit puro, `now` fijo):
  1. posición fresca y precisa **dentro** → autorizado;
  2. posición **justo en el borde** (`distance + accuracy == radius`) → bloqueado;
  3. posición **justo dentro** del borde → autorizado (frontera estricta);
  4. posición **fuera** del radio → bloqueado;
  5. posición **stale** (> 5 min) → bloqueado aunque esté dentro; exactamente 5 min sigue fresco;
  6. **accuracy > 50 m** → bloqueado aunque esté dentro y fresca; accuracy == 50 se acepta; accuracy `null` →
     bloqueado.
- `safety/GeofenceEvaluationTest` (Spring + H2, MockMvc, bean `Clock` fijo):
  1. **decisión autorizada registrada** — el endpoint admin crea la política v1 (`201`) y la seam evalúa → 
     `authorized`, `policyVersion=1`, con `trackingEvidenceId`; se persiste una fila en `safety_decisions`;
  2. **proveedor ajeno no puede crear política** → `403`;
  3. **append-only entre versiones** — una decisión ya persistida bajo v1 sigue referenciando v1 tras crear
     v2; una evaluación nueva usa v2 y **se agrega** (2 filas, nunca update);
  4. **freshness con reloj inyectado** — a `T0` la posición es fresca (autorizado); el mismo dato 6 min
     después (avanzando el `Clock`) queda **stale** (`Blocked(STALE)`).


---

## 8. Fronteras y decisiones de arquitectura

- Módulo nuevo **`safety`** (no se añadió a `BUSINESS_MODULES` de `ModuleBoundaryRulesTest`; mismo
  tratamiento que `tracking`/`fleet`/`supply`). Respeta la frontera por diseño (solo `*.api` ajenas +
  `shared` + dominio).
- **`GeofencePolicies`/`GeofenceEvaluation`** son las superficies públicas de `safety`. La decisión **no** se
  cablea al lifecycle en T17-A: cualquier integración (registrar la decisión al avanzar el delivery) es
  T17-B.
- Seams consumidas: `tracking.api.DeliveryTrackingQuery` (posición + evidencia), `fulfillment.api.DeliveryTrackingLookup`
  (tenant del delivery para la administración), `iam.api.TenantAccess` (auth), `shared.events` (outbox).
- **OpenAPI/ledger v1 intactos** (ruta nueva v2). **Migración aditiva `V22`** (primera de S17; `V21` es de
  T16-A).

---

## 9. Estado del build

Verificado el 2026-09-26 con `./mvnw.cmd -B test` y JDK 26.0.2: 249 tests, 0 fallos, 0 errores, 4 omitidos.
La validación de esquema MySQL sigue pendiente.

---

## 10. Fuera de alcance de T17-A

- **T17-B** — integrar la decisión al flujo de delivery (registrar/consultar la decisión al avanzar estados);
  aquí la seam existe pero no se dispara desde el lifecycle.
- **T18-A/B** — `ValveStateObserved`, comando firmado/outbox/ACK de válvula (**detection-only**).
- **T21-A/B** — journal/timeline de negocio.
- Sin endpoint de decisión ni de consulta de decisiones (no pedido); `latestForDelivery` queda para T17-B.
- **U09** ya está resuelta y aplicada (círculo, 5 min, 50 m, borde bloquea); no hay asunción abierta en
  T17-A.
