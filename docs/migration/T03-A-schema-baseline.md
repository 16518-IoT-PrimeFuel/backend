# T03-A — Inventario de esquema y baseline

Estado: inventario aprobado; baseline sintético y migraciones aditivas hasta
V15 implementados.

## Decisión de migrador

Se usa Flyway. El esquema actual es relacional y el DDL requerido es SQL
explícito; Flyway versiona el baseline y permite que Hibernate valide el
esquema sin `update`.

## Esquema AS-IS declarado por JPA

### Tablas de entidades (15)

| Bounded context actual | Tabla | Entidad |
|---|---|---|
| IAM | `users` | `UserPersistenceEntity` |
| IAM | `roles` | `RolePersistenceEntity` |
| IAM | `buyer_companies` | `BuyerCompanyPersistenceEntity` |
| IAM | `provider_companies` | `ProviderCompanyPersistenceEntity` |
| IAM | `password_reset_tokens` | `PasswordResetTokenEntity` |
| Inventory | `fuel_products` | `FuelProductPersistenceEntity` |
| Equipment | `equipment` | `EquipmentPersistenceEntity` |
| Ordering | `fuel_requests` | `FuelRequestPersistenceEntity` |
| Ordering | `fuel_orders` | `FuelOrderPersistenceEntity` |
| Payment | `payments` | `PaymentPersistenceEntity` |
| Fulfillment | `drivers` | `DriverPersistenceEntity` |
| Fulfillment | `vehicles` | `VehiclePersistenceEntity` |
| Fulfillment | `deliveries` | `DeliveryPersistenceEntity` |
| Notification | `notifications` | `NotificationPersistenceEntity` |
| Catalog | `provider_ratings` | `ProviderRatingPersistenceEntity` |

### Tablas auxiliares declaradas (2)

- `user_roles`, relación entre usuarios y roles.
- `provider_company_fuel_types`, colección de tipos ofrecidos por proveedor.

Total declarado: **17 tablas**.

## Mutaciones de esquema actuales

| Fuente | Perfil/condición | Mutación |
|---|---|---|
| Flyway | `dev`/`mysql` | ejecuta `db/migration/V1__synthetic_legacy_baseline.sql` |
| Hibernate | `dev`/`mysql` | `spring.jpa.hibernate.ddl-auto=validate` |
| Hibernate | `test` | `spring.jpa.hibernate.ddl-auto=create-drop` |
| `MySqlSchemaCompatibilityInitializer` | MySQL en `ApplicationReadyEvent` | `fuel_orders.status` → `VARCHAR(30)` |
| `MySqlSchemaCompatibilityInitializer` | MySQL en `ApplicationReadyEvent` | `notifications.type` → `VARCHAR(40)` |

## Riesgos registrados

- No existe historial versionado de DDL.
- `update` puede producir drift entre entornos.
- El initializer ejecuta DDL después de iniciar la aplicación.
- No existe snapshot autorizado de un MySQL real en este workspace.
- El baseline actual es sintético: representa el modelo JPA, pero no sustituye
  la reconciliación contra metadata/backup autorizado de MySQL.

## Gate T03-A

- inventario de 15 entidades y 17 tablas documentado;
- mutaciones implícitas identificadas;
- Flyway elegido y conectado al runtime;
- la migración H2 sintética pasa un test reproducible;
- los datos mock están aislados en `src/test/resources` y no se despliegan;
- T03-C debe reconciliar V1/V15 contra un snapshot MySQL autorizado.
- `SchemaContractReconciliationTest` valida la misma superficie contractual en
  el baseline sintético; `scripts/mysql-reconcile-v15.sql` es el reporte de
  solo lectura para el snapshot MySQL autorizado.

## Rollback

El runtime debe volver temporalmente a `ddl-auto=update` y desactivar Flyway si
se necesita operar contra una base existente aún no reconciliada. No ejecutar
`flyway clean` en una base compartida.
