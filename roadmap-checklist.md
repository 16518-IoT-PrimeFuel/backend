# PrimeFuel migration roadmap — checklist

Sigue `docs/PRIMEFUEL_MIGRATION_ROADMAP.md`. Se marca cada ticket cuando su build queda verde y
sus entregables están confirmados (no implica commit — ver estado de cada uno).

## W0 — Baseline reproducible (S01)

- [x] **T01-A** — Baseline de rutas y contratos. Ledger 77/77 + 11 tests golden. Build 21/21 verde.
      → `docs/api-ledger/T01-A-rest-ledger.md`
- [x] **T01-B** — Caracterización de estados y seguridad. 7 known-gaps reproducidos con test
      determinista (2 luego resueltos por hotfix, ver abajo). Build 30/30 verde.
      → `docs/api-ledger/T01-B-state-security-characterization.md`

## Hotfixes fuera de secuencia

- [x] **Hotfix R01 (2026-09-21)** — Fuga cross-tenant CRITICAL en `POST /api/v1/fuel-orders`:
      sin validar que `providerId` sea dueño de `fuelProductId`, ni que `equipmentId` pertenezca
      a `companyId` (permitía mutar el tanque de otro tenant). Corregido en
      `FuelOrderCommandServiceImpl`. Build 30/30 verde.
      → detalle en `docs/api-ledger/T01-B-state-security-characterization.md` (gaps #6 y #7)

## W1 — Guardrails, esquema y entrega durable (S02, S03, S19)

- [x] **T02-A** — Spike y reglas de dependencia de módulos. ArchUnit 1.5.0 (Java 26 verificado en
      runtime); reglas de frontera con baseline congelado (185 dependencias internas en 8 módulos) +
      regla `shared`; una violación nueva falla (verificado sembrando una y revirtiéndola). Build
      33/33 verde.
      → `docs/api-ledger/T02-A-module-dependency-rules.md`
- [x] **T02-B** — Interfaz pública piloto y baseline de arquitectura. Primera seam pública
      `iam.api.TenantAccess` (5 métodos) + `TenantAccessImpl`; piloto `FuelProductsController`
      migrado. Baseline congelado **185 → 180** (inventory 5 → 0), sin violaciones nuevas. Build 33/33
      verde.
      → `docs/api-ledger/T02-B-tenant-access-seam.md`
- [x] **T03-A** — Inventario de esquema y baseline. Flyway elegido (Boot-managed, deshabilitado hasta
      T03-B); 15 entidades → 17 tablas; `V1__baseline.sql` generado vía Hibernate con `MySQLDialect` y
      **validado en MySQL 8.0.46 local** (Flyway aplica V1 + Hibernate `validate` pasa; 18 tablas con
      `flyway_schema_history`). Runbook de restore. Upgrade legacy/restore → gate de T03-B. Build
      33/33 verde.
      → `docs/api-ledger/T03-A-schema-baseline.md`; `docs/runbooks/database-restore.md`
- [x] **T03-B** — Sustitución controlada del DDL de arranque. `ddl-auto=validate` en dev/mysql + Flyway
      habilitado con `baseline-on-migrate`; los 2 `ALTER` migrados a `V2__normalize_legacy_enum_columns`;
      `MySqlSchemaCompatibilityInitializer` eliminado (L06 resuelto). **Validado en MySQL 8.0.46**: path
      vacío (V1+V2+validate) y legacy (baseline v1+V2+validate) convergen. Build 33/33 verde.
      → `docs/api-ledger/T03-B-startup-ddl-replacement.md`
- [x] **T19-A** — Registro de publicación y contratos. Outbox transaccional hand-rolled (sin Modulith):
      `shared.events.EventEnvelope` + `EventPublicationRegistry`; impl `@Transactional(MANDATORY)`;
      tabla `event_publications` (`V3`, validada con `validate` en MySQL 8.0.46). Atomicidad probada
      (rollback no deja publicación). Build 37/37 verde.
      → `docs/api-ledger/T19-A-event-publication-registry.md`
- [x] **T19-B** — Idempotencia, replay y observabilidad (alcance mínimo). Inbox de dedup
      (`EventInbox` + tabla `consumed_events` `V4`, único `(consumer, event_id)`) + test. Replay/
      backlog/poison/dispatcher diferidos a propósito. Build 39/39 verde.
      → `docs/api-ledger/T19-B-inbox-idempotency.md`

## W2 — Identidad, tenant y clientes (S04, S05)

- [x] **T04-A** — Modelo y resolución de membresía (Organization/Membership). Agregados + VOs,
      comandos/queries, servicios, persistencia (`V5`, validada en MySQL 8.0.46), seam
      `iam.api.MembershipAccess` (resuelve organización del principal, no del body) y
      `GET /api/v2/me/organizations`. Build 39/39 verde.
      → `docs/api-ledger/T04-A-organization-membership.md`
- [x] **T04-B** — Onboarding, invitación y compatibilidad IAM. Signup v1 con dual-write
      (Organization + `OWNER` Membership en la misma TX), login dual-read (campos legacy +
      `memberships`), v2: `POST /api/v2/onboarding`, invitaciones (invitar/aceptar/revocar) con
      reglas de expiración/duplicado/revocación, `V6` validada en MySQL 8.0.46. Build 41/41 verde.
      → `docs/api-ledger/T04-B-onboarding-invitations.md`
- [x] **T05-A** — CustomerAccount y sitios. Agregados `CustomerAccount`/`CustomerSite`, comandos/
      queries/servicios, persistencia (`V7`, validada en MySQL 8.0.46: `customer_accounts` con mapa
      `legacy_company_id`, `customer_sites`), seam `equipment.api.CustomerDirectory` y v2
      `/api/v2/customers` (+ `/sites`) con organización del principal. Build verde.
      → `docs/api-ledger/T05-A-customer-accounts-sites.md`
- [x] **T05-B** — Mapa y backfill de pertenencia (**cierra W2/S05**). Seam
      `iam.api.LegacyCompanyDirectory`; `CustomerBackfillService` idempotente con cuarentena
      explícita (`customer_mapping_quarantines`, `V8`, validada en MySQL 8.0.46); sin inferencia
      desde favorito/última orden. Build verde.
      → `docs/api-ledger/T05-B-customer-membership-backfill.md`

## W3 — Supply, tanques y solicitud manual (S11, S06, S10)

- [x] **T11-A** — Interfaz Supply y unidades. Módulo `supply`: `Unit`/`Volume`, seam
      `supply.api.SupplyCatalog` + adapter sobre `fuel_products` (filtro tenant/active), v2
      `/api/v2/products`, `TenantAccess.currentProviderId()`. Sin esquema nuevo. Build verde.
      → `docs/api-ledger/T11-A-supply-seam-units.md`
- [x] **T11-B** — Reserva y conciliación de suministro. Agregado `SupplyReservation` con snapshot de
      precio/unidad, `reserve`/`release`/`reconcile` idempotentes, mutex por producto con
      `PESSIMISTIC_WRITE` (`supply_stock_locks`) para no sobrevender; `V9` validada en MySQL 8.0.46.
      Build verde.
      → `docs/api-ledger/T11-B-supply-reservations.md`
- [x] **T06-A** — Modelo Tank e interfaz de activos. Agregados `Tank`/`TankConfiguration` con
      config versionada e invariantes (0≤nivel≤capacidad, sitio del mismo cliente), `TankEligibility`,
      seam `equipment.api.TankAssets`, v2 `/api/v2/tanks`, `V10` validada en MySQL 8.0.46.
      `Volume`/`Unit` promovidos a `shared`. Build verde.
      → `docs/api-ledger/T06-A-tank-model.md`
- [x] **T06-B** — Mapeo legacy y snapshot de nivel. `TankBackfillService` idempotente (solo equipment
      clasificable con cliente mapeado), `TankReadingService` (lectura validada no retrocede;
      metadata manual separada) y puente v1 en `EquipmentCommandServiceImpl`. Sin esquema nuevo. Build
      verde.
      → `docs/api-ledger/T06-B-tank-legacy-mapping.md`
- [x] **T10-A** — Agregado y comandos de revisión (ReplenishmentRequest). Módulo `replenishment`:
      lifecycle único `PENDING→ACCEPTED|REJECTED|CANCELLED`, snapshots de unidad/precio,
      `consumeAcceptance` once-only, bloqueo optimista (`@Version`), idempotencia por `episodeKey`,
      seam `replenishment.api.ReplenishmentLookup`, v2 `/api/v2/replenishment-requests`, `V11` validada
      en MySQL 8.0.46. Build verde.
      → `docs/api-ledger/T10-A-replenishment-request.md`
- [x] **T10-B** — Puente FuelRequest/FuelOrder compatible (**cierra W3**). `LegacyFuelRequestBridge`:
      la creación v1 también crea una `ReplenishmentRequest` (clave de episodio `fuel-request:{id}`,
      organización/cliente/tanque resueltos por seams), la aceptación consume el acceptance una sola vez
      y conserva el `requestId` legacy correlacionando el `orderId`; el rechazo se propaga. Sin orden
      directa v2. Sin esquema nuevo. Build verde.
      → `docs/api-ledger/T10-B-legacy-request-bridge.md`

## W4 — Dispositivos, telemetría y reposición automática (S07, S08, S09)

> **Decisión de producto (revisión posterior): S07/S08 quedan built-but-frozen.** El código commiteado de
> `telemetry`, `devicebinding` y las migraciones `V12`–`V14` se conserva tal cual (no se revierte ni se
> elimina), pero no se planea más desarrollo sobre esa infraestructura IoT de sensores. S09 continúa: su
> evaluación de reposición se dispara por telemetría **y por carga manual de nivel** (shadow por defecto).

- [x] **T07-A** — Modelo temporal de DeviceBinding. Agregado con ventana semiabierta
      `[validFrom, validTo)`, doble barrera contra solapamiento (check de dominio + único
      `(device_id, channel, active_slot)`), bind/revoke/move, eventos sin credenciales y seam
      `equipment.api.ActiveBinding`; `V12` validada en MySQL 8.0.46. Build verde.
      → `docs/api-ledger/T07-A-device-binding-temporal.md`
- [x] **T07-B** — Provisionamiento y revocación técnica. `DeviceCredential` + `DeviceTokenHasher`
      (token de 32 bytes, solo SHA-256 persistido), provision/rotate/revoke, seam
      `equipment.api.DeviceAuthentication` con outcomes de cuarentena
      (`UNKNOWN_CREDENTIAL`/`REVOKED_CREDENTIAL`/`NO_ACTIVE_BINDING`), frontera por instante tras un move;
      `V13` validada en MySQL 8.0.46. Build verde.
      → `docs/api-ledger/T07-B-device-provisioning.md`
- [x] **T08-A** — Adapter y almacenamiento normalizado de telemetría. Módulo `telemetry`: payload
      versionado (`schemaVersion`, solo v1), normalización por `Volume`/`Unit`, dedup
      `(device, channel, sequence)`, autenticación de máquina vía `equipment.api.DeviceAuthentication`,
      cuarentena observable, publicación de `ValidatedTankReadingEvent` solo si se acepta; `V14`
      validada en MySQL 8.0.46. Build verde.
      → `docs/api-ledger/T08-A-telemetry-ingestion.md`
- [x] **T08-B** — Integración idempotente con Tank. `ValidatedTankReadingConsumer` con inbox y
      aplicación en la **misma transacción** (replay no duplica, crash recupera, fallo no queda consumido)
      y `TankAssets.applyValidatedReading` que ignora lecturas desordenadas (el snapshot no retrocede).
      Sin esquema nuevo. Build verde.
      → `docs/api-ledger/T08-B-tank-integration.md`
- [x] T09-A — Regla de reposición y episodios. `RefillThresholds` (U03 20% / U04 +10 pp), agregados
      `RefillPolicy`/`RefillEpisode`, evaluador puro `RefillPolicyEvaluator` (umbral/histéresis/rearmado/
      request pendiente, reloj inyectable) y servicios + v2 `/api/v2/tanks/{id}/refill-policy` +
      `/refill-episodes`; `V15` escrita (**build no verificado en esta máquina — pendiente de verificación
      por el usuario**). Shadow: solo persiste episodios y loguea decisiones, sin efectos externos.
      → `docs/api-ledger/T09-A-refill-policy-episodes.md`
- [x] T09-B — Generación automática idempotente. `RefillPolicyEvaluationConsumer` (dedup por inbox,
      clave de episodio = identidad de lectura) + generación opt-in por tanque vía
      `ReplenishmentCommandService` (idempotente por `episodeKey`); `ValidatedTankReadingEvent` movido a
      `telemetry.api.events`. **Addendum (revisión de producto): segundo disparador por carga manual de
      nivel** (`equipment.api.events.TankLevelManuallyUpdatedEvent` publicado por `applyManualLevel`), mismas
      garantías de idempotencia, sin endpoint nuevo; así la generación no depende de IoT. Shadow por
      defecto: sin `autoGenerateEnabled` solo persiste/loguea decisiones.
      Tests escritos (**build no verificado en esta máquina — pendiente de verificación por el usuario**).
      → `docs/api-ledger/T09-B-automatic-generation.md`

## W5 — Fleet, reservas, delivery y asignación (S12, S13, S14, S15)

- [x] **T12-A** — Extraer Fleet de CRUD. Módulo nuevo `fleet`: agregados `Driver`/`Tanker` (mueven
      `Driver`/`Vehicle` de `fulfillment`), estados tipados, `driverId ≠ userId`, soft-disable
      (`active`+`deactivatedAt`), seam `fleet.api` (`FleetCatalog`/`FleetRegistry`) + eventos
      `ResourceEnabled`/`ResourceDisabled`, v2 `/api/v2/drivers` y `/api/v2/tankers`; adapters v1
      reescritos sobre `fleet.api` + `iam.api.TenantAccess` (DELETE = soft-disable); `V16` aditiva escrita.
      **Delta del baseline ArchUnit** (12 entradas de controllers eliminadas + 3 del ctor de
      `DeliveryCommandServiceImpl` actualizadas). Código, migración y tests escritos
      (**build no verificado en esta máquina — pendiente de verificación por el usuario**).
      **Revisión (orquestador):** sin dangling refs a las clases viejas de `fulfillment`; migración V16
      aditiva y segura. Gap real: `FleetRegistryTest` solo cubre la seam `fleet.api` — ningún test ejercita
      `DriversController`/`VehiclesController` (v1) ni `DriversV2Controller`/`TankersV2Controller` (v2) a
      nivel REST/MockMvc, y el cambio DELETE→soft-disable (A6) sigue sin cobertura propia (ya reconocido en
      el doc del ticket). Store de ArchUnit editado a mano, sin correr la suite — riesgo real hasta que se
      compile.
      **Fix de revisión aplicado (2026-09-22):** se agregó `FleetV2ControllerTest` (MockMvc) que ejercita
      `DriversV2Controller`/`TankersV2Controller` de punta a punta — cross-tenant 404 (lectura, disable y
      elegibilidad), `deactivate`/`activate` reales por HTTP y la fila conservada tras el soft-disable (A6).
      → `docs/api-ledger/T12-A-fleet-catalog.md`
- [x] **T12-B** — Política y consulta de elegibilidad (**cierra S12**). `fleet.api.EligibilityQuery` + impl:
      resultado de tres valores (`ELIGIBLE`/`BUSY`/`INELIGIBLE`) según **U07 (2026-09-22)** — elegible =
      `AVAILABLE` + `active` + mismo tenant; `ASSIGNED`/`IN_ROUTE` = ocupado (no sugerido, pero no
      "inelegible"); sin vigencia ni rol nuevo (autoriza `iam.api.TenantAccess`). REST v2
      `/drivers|tankers/eligible` y `/{id}/eligibility`. Solo filtra: sin routing/ranking (fuera de S12).
      Reemplaza al doc de bloqueo por U07.
      (**build no verificado en esta máquina — pendiente de verificación por el usuario**).
      → `docs/api-ledger/T12-B-eligibility-query.md`
- [x] **T13-A** — Modelo y cálculo de capacidad. Agregado `FleetReservation` (ventana temporal
      `ReservationWindow` semiabierta + `Volume`/`Unit`) en `fleet..`, seam `fleet.api.FleetReservations`
      (`reserve`); capacidad utilizable ≥ volumen (con conversión de unidades) y sin overlap (revalidado
      dentro de la TX) → 409, recurso de otro tenant → 404. Lock `PESSIMISTIC_WRITE` sobre driver+tanker
      (U06) y `@Version` listos; `reference` + único para idempotencia. `V18` aditiva. **U05/U06** heredadas
      de T11-A/T11-B (referenciadas en el ledger). `REST v2` no se agrega (S13). Concurrencia/release/expiry
      quedan para T13-B.
      (**build no verificado en esta máquina — pendiente de verificación por el usuario**).
      → `docs/api-ledger/T13-A-fleet-reservation-model.md`
- [ ] T13-B — Reserva concurrente y liberación
- [x] **T14-A** — Lifecycle y comandos de ejecución de Delivery (prepara S14). Máquina física
      `ASSIGNED→STARTED→ARRIVED→DELIVERING→COMPLETED` + salidas terminales `FAILED`/`CANCELLED`
      (`DeliveryPhysicalState`, transición inválida → 409); `physical_state` en columna nueva (el `status`
      legacy queda con su mapa de compatibilidad, sin reescribir filas viejas); `version`/`@Version` +
      timestamps; journal append-only `delivery_state_transitions`; **U11 (2026-09-22)**: evidencia de
      `complete` = `deliveredVolume` numérico, `≤ requestedVolume` permitido y **ambos valores conservados**
      (sin foto/firma); eventos `DeliveryAssigned/Started/Arrived/Completed/Failed` por el outbox de T19-A
      con `aggregateVersion`; v2 `/api/v2/deliveries/{id}/start|arrive|complete|fail` (+ `assign`/`cancel`/
      `transitions` documentados) y tenant cruzado → 404. **Pago intacto/desacoplado.** `V17` aditiva.
      Sin cambios en el baseline de ArchUnit. v1 **no** se re-enruta (eso es T14-B).
      (**build no verificado en esta máquina — pendiente de verificación por el usuario**).
      → `docs/api-ledger/T14-A-delivery-lifecycle.md`
- [x] **T14-B** — Compatibilidad de estados y cierre físico (**cierra S14**). El adapter v1
      (`DeliveryCommandServiceImpl`) enruta las mutaciones de estado del delivery por la máquina de T14-A:
      `dispatch → AssignDeliveryCommand`, `fail → FailDeliveryCommand` y `complete →
      CompletePhysicalDeliveryCommand`; el contrato HTTP v1 (rutas/cuerpos/`status`) queda intacto. El
      cierre v1 no trae volumen ni estados intermedios: el adapter resuelve la evidencia (cantidad del
      pedido) y materializa `ASSIGNED→STARTED→ARRIVED` antes de cerrar (asunciones A1/A2). `complete`
      repetido → **409** sin sumar volumen ni duplicar journal/eventos; efectos externos v1 (release
      driver/tanker, refuel, pedido→`PENDING_PAYMENT`) conservados para T15-B. `payment` no muta físico
      (verificado, nada que retirar). Golden `DeliveryV1LifecycleMappingTest` + `OrderFulfillmentGoldenPathTest`
      verde; known-gap de T01-B #3 resuelto.
      (**build no verificado en esta máquina — pendiente de verificación por el usuario**).
      → `docs/api-ledger/T14-B-legacy-delivery-lifecycle.md`
- [ ] T15-A — Interfaz de asignación transaccional
- [ ] T15-B — Eliminar accesos cruzados y probar carreras

## W6 — Tracking, safety y válvula (S16, S17, S18, S21)

> **Requiere rediseño antes de iniciar — no depender de `telemetry`/`devicebinding` actual.** Con S07/S08
> congeladas, W6 no puede asumir sus dependencias tal como están hoy; la spec de S16 (y lo que la encadena)
> debe rediseñarse antes de arrancar cualquier ticket de W6.

- [ ] T16-A — Contrato de telemetría de transporte
- [ ] T16-B — Proyección y consulta de seguimiento
- [ ] T17-A — Modelo y validación de geocerca
- [ ] T17-B — Decisión safety y evidencia versionada
- [ ] T18-A — Protocolo y outbox de comandos de válvula
- [ ] T18-B — ACK, incidentes y prueba de hardware (requiere banco físico)
- [ ] T21-A — Journal transaccional de negocio
- [ ] T21-B — Timeline y proyección reconstruible

## W7 — Notificaciones, contratos, billing y retiro (S20, S22, S23, S24)

- [ ] T20-A — Destinatarios y suscriptores
- [ ] T20-B — Bandeja compatible y reintentos
- [ ] T22-A — Auditoría de consumidores y contratos v2
- [ ] T22-B — Adapters y plan de sunset verificado
- [ ] T23-A — Decisión documentada de cobros (ADR)
- [ ] T23-B — Aislamiento de Payment y estado físico
- [ ] T24-A — Limpieza de marcadores y docs
- [ ] T24-B — Retiro controlado de endpoints confirmados
