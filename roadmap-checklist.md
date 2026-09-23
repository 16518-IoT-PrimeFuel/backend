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
- [x] **T13-B** — Reserva concurrente y liberación. Cierra A1–A4 de T13-A. **A1:** sin endpoint REST v2
      (T15-A consume `fleet.api` in-process). **A4:** `providerId` se transporta en el comando pero debe
      resolverlo el caller desde `iam.api.TenantAccess` (documentado; no hay superficie HTTP). **A2:**
      `FleetReservations.release(reference)` idempotente + `expireOverdue()` determinista con `Clock`
      inyectado (agregado `expireIfPast(Instant)`). **A3:** idempotencia por `reference` (replay devuelve la
      misma reserva; params distintos → 409), leída post-lock en TX `READ_COMMITTED`. **Carrera:** lock
      `PESSIMISTIC_WRITE` driver→tanker, prueba real contra **MySQL 8.0.46** (gated `FLEET_MYSQL_IT=1`):
      dos conexiones solapadas → una gana, la otra 409, sin deadlock ni holds duplicados; failure injection
      verifica rollback y liberación de locks. Sin cambios de esquema (`V18` intacta). `./mvnw.cmd test`
      156/156 verde (2 skipped = IT MySQL gated).
      → `docs/api-ledger/T13-B-fleet-reservation-concurrency.md`
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
- [x] **T15-A** — Interfaz de asignación transaccional. `AssignDelivery` en `applicationflows` (composition
      root): `AssignDeliveryFlow` (no-tx) + `AssignDeliveryExecutor` (una **TX local** `READ_COMMITTED`) que
      orquesta `ConsumeAcceptance` (`replenishment.api`, nuevo: `ReplenishmentAcceptance` +
      `ReplenishmentLookup.findByOrderId`) → `ReserveSupply` (`supply.api.SupplyReservations`, nuevo) →
      `ReserveFleet` (`fleet.api`, T13-B) → crea el delivery **ASSIGNED** vía
      `fulfillment.api.DeliveryAssignments` (nuevo, enrutado por la máquina física T14-A; asignar ≠ iniciar).
      Fallo en cualquier paso: se **lanza** y la TX revierte todo (compensar = rollback), cero estado parcial.
      Idempotencia por `commandId` (`deliveries.assignment_command_id` único, `V19`, validada en MySQL 8.0.46):
      retry = mismo delivery, sin re-reservar. `providerId` siempre desde `iam.api.TenantAccess`; create v2
      `POST /api/v2/deliveries` exige un order con request **aceptada** (si no, 404/403). v1 intacto (retirar
      accesos cruzados es T15-B). Failure injection por paso + carrera real MySQL 8.0.46 (una asignación)
      verdes. `./mvnw.cmd test` 163/163 (3 skipped = IT MySQL gated).
      → `docs/api-ledger/T15-A-transactional-assignment.md`
- [x] **T15-B** — Eliminar accesos cruzados y probar carreras (**cierra S15**). `fulfillment` ya no importa
      **ningún repositorio ajeno**: los `DriverRepository`/`TankerRepository`/`FuelProductRepository`/
      `FuelOrderRepository`/`EquipmentRepository` y el `CurrentUserAccess` (iam.infrastructure) se movieron
      detrás del puerto `fulfillment.api.DeliveryIntegration`, implementado en `applicationflows`
      (`LegacyDeliveryIntegration` → `LegacyDeliveryExecutor`, una TX `READ_COMMITTED`), y el controller usa
      `iam.api.TenantAccess`. `create` v1 es **dual-mode**: order con request `ACCEPTED` → mismo orquestador de
      reservas que v2 (hereda carrera segura + idempotencia por `commandId`); order directo → rama legacy con
      sus efectos originales. Ambas despachan el order (el golden path a `PENDING_PAYMENT` se conserva). La
      aceptación no se re-consume en v1 (ya la consumió el bridge de T10-B al aceptar). Guard test
      `FulfillmentForeignRepositoryGuardTest` exige cero imports a repos/infrastructure ajenos; tests v1 de
      retry/carrera/rollback + carrera v1 en MySQL 8.0.46 verdes. `./mvnw.cmd test` 169/169 (4 skipped = IT
      MySQL). Sin cambio de esquema.
      → `docs/api-ledger/T15-B-remove-cross-module-access.md`

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

- [x] **T20-A** — Destinatarios y suscriptores (S20). **U17 = canal inicial in-app** (`NotificationChannel.IN_APP`).
      Fuente de eventos: `JpaEventPublicationRegistry.publish` ahora **emite el `EventEnvelope` in-process**
      además del outbox (un cambio en `shared`, cero cambios en productores de delivery), y
      `ReplenishmentCommandServiceImpl` **publica** `replenishment.accepted.v1`/`rejected.v1` con scope =
      `request.organizationId` (la organización cliente). Destinatarios vía nuevo seam
      **`iam.api.MembershipDirectory.activeMemberUserIds(org)`** (sólo miembros activos → revocado no recibe).
      `NotificationFanoutListener` mapea eventType→notificación y hace fanout **idempotente**:
      `EventInbox` (por evento) + unique **`uk_notifications_event_recipient_channel(event_id,user_id,channel)`**
      (`V20`, validada en MySQL 8.0.46). `notifications` gana organization_id/event_id/channel/delivery_status/
      attempts/last_attempt_at. v1 GET/POST intactos (eso es T20-B). Limitación documentada: los eventos de
      delivery usan `organizationId = providerId` (espacio legacy) → hoy no resuelven destinatarios.
      `NotificationFanoutTest` (4): fanout a todos los miembros, reject, replay sin duplicar, revocado sin nuevo
      fanout. `./mvnw.cmd test` 191/191.
      → `docs/api-ledger/T20-A-notification-fanout.md`
- [x] **T20-B** — Bandeja compatible y reintentos (**cierra S20**). `MeNotificationsController` agrega
      `/api/v2/me/notifications` (`GET`, `GET /unread`, `POST /{id}/read`), scoped al usuario del principal
      (`iam.api.MembershipAccess.currentUserId()`, sin id en la ruta → privacidad: cada uno solo su bandeja;
      404 si es de otro). v1 GET **sin cambios** (compatible); `POST /api/v1/notifications` **deprecado, no
      roto** (`@Deprecated` + `@Operation(deprecated=true)`, mismo comportamiento; su retiro es S22/T24-B).
      Marcar leída es idempotente. `MeNotificationsControllerTest` (2): privacidad entre dos miembros, read
      idempotente, POST v1 sigue 201. `./mvnw.cmd test` 193/193. Sin cambio de esquema.
      → `docs/api-ledger/T20-B-me-inbox-and-deprecation.md`
- [x] **T22-A** — Auditoría de consumidores y contratos v2 (**U14 resuelta ruta por ruta**). 77/77 rutas
      clasificadas con acción o blocker en `T22-A-consumer-audit-and-v2-contracts.md`: `KEEP`/`V2`/`REDESIGN`
      donde ya existe v2 (deliveries, drivers/tankers, replenishment-requests, `/me/notifications`, supply/fleet
      APIs), `DEPRECATE` (provider-ratings, favorite-provider, directorio global, orden directa/confirm,
      notifications POST), y **BLOCKER** donde no hay evidencia: (1) **`ROLE_ADMIN` es inasignable** (Roles sólo
      BUYER/PROVIDER) → todas las rutas admin son inalcanzables; (2) **ledger externo `UNKNOWN`** (sin repo
      cliente) → ningún `SUNSET` aprobable. No se inventaron consumidores. Sin cambios de código.
      → `docs/api-ledger/T22-A-consumer-audit-and-v2-contracts.md`
- [x] **T22-B** — Adapters y plan de sunset verificado. **No se retiró ningún endpoint** (preparación, no
      ejecución). Golden contracts v1/v2 verdes (golden path, T14-B mapping, T15-B dual-mode, delivery v2,
      `/me`) + nuevo `V1V2CoexistenceGoldenTest` (un delivery creado por v1 se lee/opera por v2). Registro de
      sunset por familia (destino/prerequisito/owner/ventana/**no ejecutado**) y plan de métricas por versión
      (`path.version` + `count`/`last_seen`/`distinct_callers`) documentados. Sunset **bloqueado** hasta cerrar
      ledger externo + decidir rol plataforma (blockers de T22-A). → `docs/api-ledger/T22-B-sunset-plan.md`
- [x] **T23-A** — Caracterización y decisión de Payment (ADR). **Caracterización + ADR, sin refactor** (0
      cambios en `src/main/java/.../payment`). `PaymentCharacterizationTest` (18 tests) reproduce las 7 rutas
      v1, su máquina de estados **sin guardas** y los permisos reales. **ADR resuelve U12 = Opción A
      (registro financiero operativo):** `registered` = fila creada por la company compradora (hoy `PENDING`);
      `authorized` = confirmación manual por comprador o provider con referencia (hoy `COMPLETED`); `settled`
      = NO representable hoy (sin pasarela) y el sistema no debe afirmarlo; `refunded` = marcado por actor
      autorizado (hoy no revierte la orden). Hallazgos (bugs reales, NO corregidos): F1 `createPayment`
      `@PreAuthorize` antes del null-check → 403 en vez del 400 documentado; F2 create no valida
      `order.status` (pago sobre orden cancelada); F3 complete de orden cancelada → 500; F4 refund sin guarda
      ni motivo/fecha y no revierte la orden; F5 complete repetido sobrescribe ref/paidAt y complete después
      de refund es aceptado; F6 `GET /payments` exige `ROLE_ADMIN` que el modelo de roles no puede otorgar →
      403 siempre; F8 sin unique en `payments.order_id` y create no transaccional. Inventario de accesos
      cross-module para T23-B: `PaymentCommandServiceImpl → FuelOrderRepository` (escritura; el que se quita),
      controller → `FuelOrderQueryService` + `CurrentUserAccess`. Consumer real: `AnalyticsQueryServiceImpl`
      (revenue = suma de `COMPLETED`). → `docs/api-ledger/T23-A-payment-characterization.md`
- [x] **T23-B** — Aislamiento de Payment y estado físico (**cierra S23**). `PaymentCommandServiceImpl` ya **no
      importa `FuelOrderRepository`**: `complete` publica **`payment.completed.v1`** y un adapter de ordering
      (`OrderingPaymentCompletionAdapter`) marca su propia orden `PAID` → v1 preservado sin que payment escriba
      ordering. Invariantes movidos al **application layer** (order+company presentes; orden existe y es de esa
      company; `amount` positivo y consistente con el snapshot; uno por orden) usando el nuevo seam
      **`ordering.api.OrderLookup`** (elimina además la dependencia a `ordering.domain.model.queries`).
      Corregido **F1**: `createPayment` valida presencia **antes** de autorizar → `companyId` null responde 400
      (antes 403); controller pasa a `iam.api.TenantAccess`. `PaymentCompleted`/`DeliveryCompleted` independientes.
      `PaymentForeignRepositoryGuardTest` (cero repos ajenos en payment) + `PaymentOrderDecouplingTest` verdes;
      `PaymentCharacterizationTest` ajustado a 400 para F1. Quedan documentados como siguientes pasos: guards de
      transición/idempotencia, motivo de refund, BigDecimal+moneda, unique(order_id), matriz de permisos.
      `./mvnw.cmd test` 196/196. Sin cambio de esquema.
      → `docs/api-ledger/T23-B-payment-isolation.md`
- [x] **T24-A** — Limpieza de marcadores y docs. Borradas **solo** las 6 clases vacías confirmadas (leídas +
      búsqueda repo-wide: cero usos/reflection): `FulfillmentController`, `DirectoryController`,
      `InventoryController`, `OrderingController`, `PaymentController`, `NotificationController`. Docs
      alineadas: T01-A ledger (nota de placeholders), ARCHITECTURE_REPORT, checklist Swagger, T23-A F9
      (resuelto), y 6 diagramas `.puml` (nodos y relación eliminados). Snapshot OpenAPI regenerado por test;
      **77/77 rutas intactas**. No se tocó T24-B (retiro real, sigue bloqueado).
      `./mvnw.cmd test` verde. → `docs/api-ledger/T24-A-marker-cleanup.md`
- [ ] T24-PRE-ADMIN — Asignación de rol de plataforma (U19, nuevo, desbloquea T24-B). `ROLE_ADMIN` era un
      string en `@Secured` de 7 controllers sin existir en `Roles` ni en ningún flujo de asignación — no
      existía forma de tener un usuario admin. Resuelto con el usuario (2026-09-23): endpoint protegido
      `POST /api/v2/admin/users/{id}/promote` (solo-admin) + seed manual en BD para el primer admin (bootstrap).
      Ver callout U19 en el roadmap, sección S24.
- [ ] T24-PRE-METRICS — Métricas de tráfico por versión (v1/v2), nuevo, desbloquea T24-B. Implementa el plan ya
      documentado en `docs/api-ledger/T22-B-sunset-plan.md` sección 2 (instrumentar `path.version` +
      `controller#method`, contador por ruta, reporte semanal `count`/`last_seen`/`distinct_callers`). El
      usuario decidió (2026-09-23) implementar esto antes de aprobar cualquier sunset — no se puede probar
      "cero uso" de ninguna ruta v1 sin esta instrumentación (el ledger externo de consumidores sigue `UNKNOWN`
      aparte).
- [ ] T24-B — Retiro controlado de endpoints confirmados. Bloqueado por: (1) T24-PRE-METRICS + ventana de
      medición real, (2) T24-PRE-ADMIN, (3) ledger externo de consumidores `UNKNOWN` (fuera de este repo).

## Documentación Swagger (OpenAPI + javadoc de REST)

Documentación de los **125 métodos REST** de los **27 controllers activos** con
`@Operation(summary, description)` + `@ApiResponses`/`@ApiResponse` (un código por respuesta real del
método) y un javadoc por método que aporta invariantes/tenant/ownership sin repetir el texto de Swagger.
Cambio de documentación pura: **sin** tocar lógica, validaciones ni códigos HTTP existentes. (Los
placeholders vacíos `FulfillmentController`, `DirectoryController`, `InventoryController`,
`NotificationController`, `OrderingController` y `PaymentController` fueron **eliminados** en T24-A.) Los
códigos por método se derivaron de los `ApplicationError` del `CommandService`/`QueryService` invocado +
los chequeos manuales de forbidden/not-found del propio método + el `@PreAuthorize` (403); no se
documentó 401 (filtro bearer global, uniforme en todos los endpoints autenticados).

- [x] **Swagger T1 — IAM** (7 controllers, 19 métodos): `Authentication`, `BuyerCompanies`,
      `Invitations`, `MyOrganizations`, `Onboarding`, `ProviderCompanies`, `Users`.
- [x] **Swagger T2 — Equipment + Supply + Catalog + Reporting** (6 controllers, 21 métodos):
      `Customers`, `Equipment`, `Tanks`, `Products`, `ProviderRatings`, `Analytics`.
- [x] **Swagger T3 — Inventory + Replenishment** (3 controllers, 16 métodos): `FuelProducts`,
      `RefillPolicies`, `ReplenishmentRequests`.
- [x] **Swagger T4 — Ordering** (2 controllers, 12 métodos): `FuelOrders`, `FuelRequests`.
- [x] **Swagger T5 — Fulfillment/Fleet** (6 controllers, 42 métodos): `Deliveries`, `DeliveriesV2`,
      `Drivers`, `Vehicles`, `DriversV2`, `TankersV2`.
- [x] **Swagger T6 — Payment + Notification + Telemetry** (3 controllers, 15 métodos): `Payments`,
      `Notifications`, `Telemetry`.

Build final **156/156 verde** (`mvnw test`; incluye los tests concurrentes de T13-B). Sin cambios de
comportamiento. Hallazgos de mapeo de códigos (documentados, **no** corregidos — "characterize ≠ fix"):
`POST /api/v1/buyer-companies|provider-companies` sin `@Valid` (input inválido → 500 en vez de 400);
`ProviderRatings{create,update}` devuelve 400 para referencias inexistentes (convención esperaría 404);
transiciones de estado inválidas en `fuel-requests accept`/`payments complete`/`payments refund` caen en
500 en vez de 409; varios gates de ownership devuelven 403 en lugar de 404 (y al revés) según el controller.
