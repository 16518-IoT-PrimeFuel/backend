# T12-A — Extraer Fleet de CRUD (S12)

Parent spec: S12. Preconditions: T04-B, T02-B.

> **Build no verificado en esta máquina — pendiente de verificación por el usuario.** Este equipo no tiene
> SDK de Java: no se ejecutó `compile`, `test` ni el arranque MySQL. El código, la migración y las pruebas
> quedan para que el usuario compile, corra `mvnw test` y valide el esquema con
> `scripts/validate-schema-mysql.ps1`. **Además, esta entrega toca el baseline congelado de ArchUnit** (ver
> más abajo): conviene correr la suite para confirmar que el store quedó consistente.

## What was added (módulo nuevo `fleet`)

- **Agregados `Driver` y `Tanker`** (mueven `Driver`/`Vehicle` de `fulfillment` al módulo dueño `fleet`):
  - tenant explícito (`providerId`);
  - **`driverId ≠ userId`**: `Driver` lleva un `userId` opcional (vínculo IAM) separado de su propia
    identidad, nunca fusionado;
  - **estados tipados** (`DriverStatus`, `TankerStatus`) validados en el borde (un código desconocido se
    rechaza; en blanco → `AVAILABLE`);
  - **soft-disable** con `active` + `deactivatedAt`: `deactivate()`/`activate()` no borran la fila
    (invariante "desactivar no borra histórico");
  - `update(...)` **no puede transferir el tenant**: el comando de actualización no incluye `providerId`.
- **`fleet/api`** (única superficie pública):
  - `FleetCatalog` con snapshots inmutables (`DriverSnapshot`, `TankerSnapshot`, incluyen `active`);
  - `FleetRegistry` con `register/update/deactivate/activate` para driver y tanker (devuelven `Result`);
  - `fleet/api/events`: `ResourceEnabledEvent` y `ResourceDisabledEvent`.
- **Persistencia** `fleet/infrastructure/persistence`: entidades sobre las tablas `drivers`/`vehicles`
  (el vehículo se modela como `Tanker`), repos, assemblers y adapters.
- **REST v2**: `/api/v2/drivers` y `/api/v2/tankers` (`POST`, `GET`, `GET /{id}`, `PUT`,
  `POST /{id}/deactivate`, `POST /{id}/activate`). El tenant sale **siempre del principal**
  (`iam.api.TenantAccess.currentProviderId()`), nunca del body.
- **Migración aditiva** `V16__fleet_lifecycle_and_user_link.sql`: `drivers.user_id`, `drivers.active`,
  `drivers.deactivated_at`, `vehicles.active`, `vehicles.deactivated_at` (con `default 1` para no romper
  filas existentes).
- **Nada fuera del módulo accede a los repos de Fleet**: se eliminaron los `DriverRepository`/
  `VehicleRepository` y sus entidades/adapters de `fulfillment`.

## Adapters v1 (compatibilidad)

`fulfillment.interfaces.rest.DriversController` y `VehiclesController` se reescribieron como adapters
finos sobre `fleet.api`, conservando ruta, body y códigos de estado:

- dejan de usar los repositorios de dominio y usan `FleetCatalog`/`FleetRegistry`;
- autorizan por la seam pública `iam.api.TenantAccess` (bean `tenantAccess`) en vez de la infra
  `CurrentUserAccess` (por eso desaparecen violaciones congeladas, ver delta);
- **`DELETE` pasa a soft-disable** (mismo 204, la fila se conserva) — redesign aprobado por S12;
- el `PUT` mantiene sus chequeos de ownership pero ya **no** permite cambiar de proveedor (invariante
  "update nunca transfiere tenant");
- la forma de `DriverResource`/`VehicleResource` (v1) no cambia.

`DeliveryCommandServiceImpl` (delivery legacy) solo cambió el **import/tipo** de los repositorios movidos
(`fleet.domain.repositories.DriverRepository`/`TankerRepository`); su cuerpo quedó intacto a propósito. Su
acceso crudo a repositorios de Fleet **se difiere a T15-B** ("eliminar accesos cruzados"), que es el ticket
dueño de ese retiro.

## Delta del baseline de ArchUnit (explicitar)

`src/test/resources/archunit_store/b44170e8-84f9-41d2-9d8a-4c9e08869fa7` (regla del módulo `fulfillment`):

- **Se eliminaron 12 entradas** de `DriversController`/`VehiclesController` (constructor, campo y llamadas
  a `CurrentUserAccess.ownsProvider`): esos controllers ya no dependen de `iam.infrastructure..`.
- **Se actualizaron 3 entradas** del constructor de `DeliveryCommandServiceImpl` para reflejar las nuevas
  rutas de `DriverRepository`/`TankerRepository` (`fleet.domain.repositories.*`). Los parámetros prohibidos
  (equipment/inventory/ordering) y las entradas de método (con números de línea) **no cambian**.

`FreezingArchRule` reduce el store automáticamente ante violaciones resueltas y falla ante violaciones
nuevas; por eso las 12 eliminaciones son seguras y las 3 líneas del constructor deben coincidir exactamente
(revisar al correr la suite).

## Tests

`FleetRegistryTest` (Spring + H2): la identidad del driver y el `userId` quedan separados; el update no
transfiere el tenant; desactivar conserva la fila y publica `ResourceDisabledEvent`; reactivar publica
`ResourceEnabledEvent`; el catálogo está acotado al tenant; registrar tanker tipa el estado; un estado
desconocido se rechaza. Los contratos v1 siguen cubiertos por las pruebas de caracterización existentes
(`OrderFulfillmentGoldenPathTest`, `CrossTenantIsolationTest`, `StateLifecycleRetryCharacterizationTest`),
que solo crean drivers/vehicles (nunca hacen DELETE ni listan).

## Asunciones abiertas

- **A1 — `Tanker` mapea la tabla legacy `vehicles`** (no se renombra la tabla; el rename es de vocabulario).
- **A2 — `capacity`/`unit` del tanker siguen siendo `Double`/`String`** como en el legado; normalizarlos a
  `shared.Volume` se difiere a T13 (capacidad/reserva), donde la unidad pasa a ser semántica.
- **A3 — el estado del agregado se mantiene como `String`** (Lombok) para no romper el flujo de delivery
  legacy que escribe `ASSIGNED`/`IN_ROUTE`/`AVAILABLE` directamente; el tipado vive en el borde
  (`DriverStatus`/`TankerStatus` en los comandos). Tiparlo por completo depende de T14/T15.
- **A4 — `fleet` no está en `BUSINESS_MODULES`** de `ModuleBoundaryRulesTest` (misma decisión que
  `supply`/`replenishment`/`telemetry`, ver T10-A A3). Incorporarlo se hará al revisar el inventario de
  fronteras; hoy el módulo solo cruza por `iam.api` y expone `fleet.api`.
- **A5 — el acceso crudo de `DeliveryCommandServiceImpl` a repositorios de Fleet se difiere a T15-B**
  (roadmap: "eliminar accesos cruzados").
- **A6 — `DELETE` v1 pasó de hard-delete a soft-disable**; el cambio de comportamiento no está cubierto por
  las pruebas de caracterización actuales (solo crean, no borran) y es el diseño aprobado por S12.
- **A7 — `docs/diagrams/fulfillment.puml` queda desactualizado** (todavía dibuja `DriverRepository`/
  `VehicleRepository` en `fulfillment`). Regenerar diagramas es alcance de T24-A ("docs/runtime coinciden"),
  no de este ticket.
- **A8 — RESUELTO (2026-09-22, fix de revisión).** Los controllers de `fleet` ya tienen cobertura REST:
  se agregó `FleetV2ControllerTest` (MockMvc) que dispara peticiones HTTP reales contra
  `DriversV2Controller`/`TankersV2Controller` — cross-tenant 404, `deactivate`/`activate` reales y la fila
  conservada tras el soft-disable (A6), además de la elegibilidad (T12-B). Los adapters v1
  (`DriversController`/`VehiclesController`) siguen cubiertos indirectamente por las pruebas de
  caracterización; su cobertura directa queda para T14-B/T15-B si se considera necesario.
