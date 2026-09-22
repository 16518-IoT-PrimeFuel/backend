# T09-A — Regla de reposición y episodios (S09)

Parent spec: S09. Preconditions: T06-B, T08-B, T10-B + U03/U04.

> **Build no verificado en esta máquina — pendiente de verificación por el usuario.** Este equipo no
> tiene SDK de Java, así que no se ejecutó `compile`, `test` ni el arranque contra MySQL. El código, la
> migración y las pruebas se escribieron como archivos y quedan para que el usuario los corra en su
> entorno (compilar + `mvnw test` + `scripts/validate-schema-mysql.ps1`).

## What was added (module `replenishment`, paquete nuevo `policy` diferido: sin paquete extra)

- **U03/U04 como política explícita.** `RefillThresholds` (record) fija el umbral de nivel bajo (**20%**
  por defecto, override por tanque) y la histéresis (**+10 pp**), y expone `rearmPercent()` = umbral +
  histéresis (30% con los defaults). Validación estricta (`0 < umbral < 100`, histéresis positiva,
  `umbral + histéresis ≤ 100`).
- **`RefillPolicy`** (agregado) por tanque: `lowLevelPercent`, `hysteresisPercent`, `targetLevelPercent`
  (a qué nivel reponer, 100% por defecto), `providerId`/`fuelProductId` opcionales,
  `autoGenerateEnabled` (**false por defecto = shadow**) y `policyVersion` que se incrementa en cada
  reconfiguración. Sin fila de policy, la evaluación usa los defaults globales.
- **`RefillEpisode`** (agregado): un episodio por necesidad. Nace `OPEN` con su `episodeKey`, el nivel de
  apertura, el target y el `requestedVolume` calculado; `markRequestEmitted(requestId)` es once-only;
  `rearm(...)` solo cierra un episodio abierto. La clave usada por el evaluador es
  `refill:{tankId}:{policyVersion}:{epochMilli}`.
- **`RefillPolicyEvaluator`** (domain service puro, `replenishment/domain/services`): dada la capacidad,
  el nivel, el target, los umbrales, el episodio abierto (si lo hay) y si existe una request pendiente,
  decide de forma determinista con instante inyectado:
  - nivel ≤ umbral y sin episodio abierto → **OPEN_EPISODE** (y `wouldCreateRequest = true`);
  - episodio abierto y nivel ≥ umbral+histéresis → **REARM_EPISODE** (rearma; el siguiente nivel bajo
    abre un episodio nuevo);
  - episodio abierto y nivel dentro de la banda → **NO_ACTION** (la histéresis suprime pedidos
    duplicados por ruido, riesgo R05);
  - sin episodio, nivel ≤ umbral, pero **request pendiente** → **SUPPRESSED_PENDING_REQUEST**
    (invariante "no otra request activa");
  - target ≤ nivel → **NO_ACTION** (no hay nada que pedir).
  Los tipos de decisión viven en `RefillDecisionType` y el resultado explicable en `RefillDecision`
  (registra `levelPercent`, `lowLevelPercent`, `rearmPercent` y `evaluatedAt`).
- **Servicios de aplicación**: `RefillPolicyCommandService` (`ConfigureRefillPolicyCommand`,
  `EvaluateRefillPolicyCommand`) y `RefillPolicyQueryService` (policy y episodios por tanque). La
  configuración rechaza reconfigurar la policy de otra organización.
- **Reloj inyectable**: `shared.infrastructure.configuration.ClockConfiguration` publica un `Clock`
  (`systemUTC`), que el servicio usa; las pruebas lo sustituyen por uno mutable.
- **Persistencia**: `refill_policies` (único `tank_id`) y `refill_episodes` (único `episode_key`, y un
  `open_slot` que vale el `tank_id` mientras el episodio está abierto y `null` una vez rearmado → único
  parcial que impide dos episodios abiertos del mismo tanque, misma doble barrera que `device_bindings`).
  `V15__refill_policies_episodes.sql` (estilo DDL generado por Hibernate, validable con MySQL).
- **REST v2**: `PUT/GET /api/v2/tanks/{tankId}/refill-policy` y `GET /api/v2/tanks/{tankId}/refill-episodes`
  (la organización sale del principal y el tanque se verifica vía `equipment.api.TankAssets`).

> Al correr la suite, `OpenApiSnapshotTest` regenera `docs/api-ledger/openapi-snapshot.json`; las rutas v2
> nuevas quedarán incluidas en ese archivo (efecto esperado). Los contadores de `/api/v1/**` siguen en 77.

**Shadow mode:** esta entrega no crea `ReplenishmentRequest`, no notifica y no tiene efectos externos.
Solo persiste episodios y **loguea** la decisión que tomaría (`"refill decision=..."`). La
generación real queda para T09-B y, por defecto, apagada.

## Tests

`RefillPolicyEvaluatorTest` (unitario puro, sin Spring): umbral exacto (20% abre; 20,1% no), histéresis
(25% dentro de la banda → no-op; 30% rearma; 29,99% no), supresión por request pendiente, target ya
satisfecho, overrides de umbral por tanque, estabilidad de la clave de episodio con instante fijo, y
entradas imposibles (capacidad 0, nivel > capacidad).

`RefillPolicyTest` (Spring + H2, reloj mutable inyectado): overrides por tanque + bump de versión;
organización ajena no puede configurar; los defaults globales abren a 20% y persisten un episodio
(con `openedAt` = instante del reloj); **100 lecturas bajas producen un solo episodio**; el episodio
solo rearma tras recuperar la banda y entonces un nuevo nivel bajo abre otro episodio; una request
pendiente suprime la apertura; **un rechazo no abre loop** (el episodio sigue abierto); la automatización
es opt-in por tanque.

## Asunciones abiertas

- **A1 — la clave de episodio usa el instante del reloj** (`refill:{tankId}:{policyVersion}:{epochMilli}`).
  Es determinista y explicable; como la evaluación se dispara por lecturas con `capturedAt` distinto, no
  se esperan colisiones. Si el reloj se congela y se intenta abrir dos episodios en el mismo instante, el
  único sobre `episode_key` lo detecta.
- **A2 — un episodio abierto por tanque.** Se refuerza con el único parcial `open_slot` (MySQL y H2
  permiten múltiples `NULL`). No se modeló un historial de "episodios planificados"; el agregado
  `RefillEpisode` guarda el cierre, que es lo que necesita la reconciliación.
- **A3 — U03/U04 se aplican solo cuando el nivel proviene de una lectura validada** (S08: las lecturas
  en cuarentena nunca llegan a un evento validado). T09-A no revalida la calidad por su cuenta.
- **A4 — `providerId`/`fuelProductId` son opcionales en la policy.** Sin ellos (o con
  `autoGenerateEnabled=false`) solo se registra la decisión; es la puerta que T09-B usa para generar.
- **A5 — `replenishment` sigue fuera de `BUSINESS_MODULES`** en `ModuleBoundaryRulesTest` (decisión
  heredada de T10-A). El código nuevo solo cruza módulos por `*.api` (`equipment.api.TankAssets`) y por el
  `shared` kernel, pero la incorporación a la lista se hará cuando se revise el inventario de fronteras.
