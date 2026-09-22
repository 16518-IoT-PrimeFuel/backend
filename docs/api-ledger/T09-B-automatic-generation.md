# T09-B — Generación automática idempotente (S09)

Parent spec: S09. Precondition: T09-A.

> **Build no verificado en esta máquina — pendiente de verificación por el usuario.** Este equipo no
> tiene SDK de Java: no se ejecutó `compile`, `test` ni el arranque MySQL. Los archivos quedan para que el
> usuario compile, corra `mvnw test` y valide el esquema con `scripts/validate-schema-mysql.ps1`.

## What was added

- **El evento de telemetría pasa a contrato público**: `ValidatedTankReadingEvent` se movió de
  `telemetry.domain.model.events` a **`telemetry.api.events`**, para que otro módulo (el policy de
  `replenishment`) pueda consumirlo sin tocar internals de `telemetry` (roadmap: "módulos ajenos solo por
  `api`/`events`"). El publicador (`TelemetryIngestServiceImpl`), el consumidor T08-B y las dos pruebas de
  telemetría apuntan al nuevo paquete. Sin cambio de payload.
- **`RefillPolicyEvaluationConsumer`** (`replenishment/application/internal/consumers`): escucha
  `ValidatedTankReadingEvent`, deduplica con `EventInbox` usando la misma identidad que la integración de
  tanque (`device:channel:sequence`) y evalúa leyendo el snapshot del tanque vía `equipment.api.TankAssets`
  (así una lectura desordenada no retrocede el nivel evaluado). Su **clave de episodio sale de la misma
  identidad de lectura** (`refill:{tankId}:{device}:{channel}:{sequence}`), de modo que reintentar una
  lectura nunca abre un segundo episodio ni genera una segunda request.
- **Orden determinista de listeners**: `ValidatedTankReadingConsumer` (T08-B) pasa a
  `@Order(HIGHEST_PRECEDENCE)` y el consumer de policy usa `@Order(LOWEST_PRECEDENCE)`, para que la
  política observe el snapshot ya actualizado por la lectura. Cambio mínimo sobre T08-B (solo orden).
- **Generación idempotente** en `RefillPolicyCommandServiceImpl`: al abrir un episodio, si el tanque optó
  por automatización (`autoGenerateEnabled` + `providerId` + `fuelProductId`) se emite
  `CreateReplenishmentRequestCommand` con `source=AUTOMATIC` y `episodeKey` = clave del episodio; la
  creación reutiliza la idempotencia ya existente en `ReplenishmentCommandServiceImpl` (único
  `episode_key`). Después se marca el episodio con `requestEmitted`/`requestId` (once-only). Si la creación
  falla, se lanza excepción para revertir toda la transacción (episodio incluido), así el trigger se puede
  reintentar sin duplicar.
- **Shadow mode es el default**: sin `autoGenerateEnabled` (o sin producto/proveedor) no se crea ninguna
  request; solo se persiste el episodio y se loguea la decisión. Ningún módulo externo se notifica.

No hay esquema nuevo en este ticket (las tablas `refill_policies`/`refill_episodes` llegaron en T09-A).

## Tests

`RefillGenerationIntegrationTest` (Spring + H2, consumer invocado de forma directa sobre un snapshot fijado
por `TankAssets`):
- **100 lecturas bajas → exactamente 1 request** (con `source=AUTOMATIC`, cantidad = target − nivel, y el
  episodio marcado `requestEmitted` con el `requestId` correlacionado);
- **shadow por defecto**: con la automatización apagada, 100 lecturas bajas no crean ninguna request (solo
  el episodio);
- **rearmado controlado**: decidir la primera request, recuperar por encima de la banda y volver a bajar
  genera un segundo episodio y exactamente una request más;
- **rechazo no abre loop**: tras rechazar la request, nuevas lecturas bajas no vuelven a generar (el
  episodio sigue abierto);
- **replay de una lectura** no duplica episodio ni request (dedup de inbox).

## Asunciones abiertas

- **A1 — el consumidor es in-process y single-instance** (misma asunción A1 de T08-B). La unicidad de
  `(consumer, event_id)` ya impide la doble evaluación; el dispatcher de outbox/backlog sigue diferido.
- **A2 — fallo de generación reintenta indefinidamente** (poison message documentado en T19-B): al revertir
  la transacción, la lectura no queda consumida y se reintenta. Solo ocurre si falta producto/proveedor o
  hay error de infraestructura.
- **A3 — la automatización exige producto y proveedor explícitos** por tanque. No se infiere el producto a
  partir de `fuelType` ni el proveedor; es una decisión operativa que el usuario configura.
- **A4 — el orden de listeners se fijó con `@Order`.** Si en el futuro se reemplaza el multicaster
  in-process por el outbox durable, el orden pasa a depender del dispatcher y habrá que revisitarlo.
- **A5 — `replenishment` sigue fuera de `BUSINESS_MODULES`** (heredado de T10-A); el cruce nuevo
  (`replenishment → telemetry.api.events`) ya respeta la regla `api/events`.

## Addendum (revisión de producto) — disparador de carga manual

Motivación: sin dispositivos IoT reales, el consumidor solo evaluaba con `ValidatedTankReadingEvent`, así
que la generación automática nunca se disparaba en producción. Se agrega un **segundo disparador** para la
carga manual de nivel (camino legacy v1), **sin quitar** el de telemetría. Sigue en shadow mode.

- **Nuevo contrato público** `equipment.api.events.TankLevelManuallyUpdatedEvent` (`tankId`,
  `organizationId`, `observedAt`, `sourceKey`), publicado por `TankReadingServiceImpl.applyManualLevel(...)`
  tras aplicar la edición manual. Ese método es el punto único alcanzado hoy desde el puente v1
  (`EquipmentCommandServiceImpl.handle(UpdateEquipmentCommand)` → `applyManualLevel`). El camino de
  telemetría no se toca y `applyValidatedReading` **no** publica este evento (nada de doble disparo).
- `RefillPolicyEvaluationConsumer` gana un segundo `@EventListener` para ese evento; ambos disparadores
  convergen en el mismo `evaluate(...)` (lee el snapshot del tanque y emite `EvaluateRefillPolicyCommand`).
- **Mismas garantías de idempotencia y no-duplicación**: mismo `EventInbox` (`consumer = "refill-policy"`) y
  la identidad del disparador como clave de dedup **y** semilla del `episodeKey`. Para manual la clave es
  `refill:{tankId}:manual:{observedAt.toEpochMilli()}`; un episodio por necesidad y sin pedidos duplicados
  en reintentos/replay, con el episodio abierto suprimiendo ediciones bajas repetidas.
- **Sin endpoint v2 nuevo**: el puente legacy v1 sigue siendo la única vía de entrada (aceptado así).
- **Shadow mode intacto**: no se generan `ReplenishmentRequest` reales salvo opt-in por tanque
  (`autoGenerateEnabled`). Sin esquema nuevo.

Tests nuevos en `RefillGenerationIntegrationTest`: `aManualLevelEditTriggersTheSameIdempotentEvaluation`
(una edición manual genera una request y las siguientes no duplican) y
`aManualLevelEditOnlyRecordsAShadowDecisionWhenAutomationIsOff` (opt-out → solo episodio, sin request).

Asunciones del addendum:

- **B1 — la identidad de la edición manual es el instante de observación** (`manual:{epochMilli}`), porque la
  actualización de equipment v1 no trae secuencia ni versión. No hay replay para el camino manual; dos
  ediciones en el mismo milisegundo se deduplicarían (mismo nivel, mismo resultado). Si en el futuro se
  necesita una identidad más fuerte, el punto de cambio es `sourceKey`.
- **B2 — el fallo de generación revierte toda la transacción**, incluida la actualización legacy de
  equipment (mismo criterio atómico que telemetría). Solo ocurre con automatización opt-in y configuración
  incompleta.
- **B3 — publicado en proceso** (`ApplicationEventPublisher`), consistente con T08/T09; el outbox durable
  sigue diferido con T19-B.
