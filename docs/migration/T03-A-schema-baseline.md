# T03-A — Inventario de esquema y baseline

Estado: inventario aprobado para implementación el 2026-09-23.

## Decisión de migrador

Se usará Flyway en el siguiente ticket. El esquema actual es relacional y el
DDL requerido es SQL explícito; Flyway permite versionar el baseline, ejecutar
expansiones y cambiar Hibernate a `validate` sin introducir una abstracción de
migración mayor que el problema.

No se añade la dependencia ni se cambia el runtime en T03-A.

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
| Hibernate | `dev` | `spring.jpa.hibernate.ddl-auto=update` |
| Hibernate | `mysql` | `spring.jpa.hibernate.ddl-auto=update` |
| Hibernate | `test` | `spring.jpa.hibernate.ddl-auto=create-drop` |
| `MySqlSchemaCompatibilityInitializer` | MySQL en `ApplicationReadyEvent` | `fuel_orders.status` → `VARCHAR(30)` |
| `MySqlSchemaCompatibilityInitializer` | MySQL en `ApplicationReadyEvent` | `notifications.type` → `VARCHAR(40)` |

## Riesgos registrados

- No existe historial versionado de DDL.
- `update` puede producir drift entre entornos.
- El initializer ejecuta DDL después de iniciar la aplicación.
- No existe snapshot autorizado de un MySQL real en este workspace.
- El baseline productivo debe generarse desde metadata/backup autorizado; no se
  debe inventar un esquema productivo a partir de H2.

## Gate T03-A

- inventario de 15 entidades y 17 tablas documentado;
- mutaciones implícitas identificadas;
- Flyway elegido sin modificar todavía el runtime;
- T03-B queda bloqueado hasta disponer de snapshot MySQL autorizado y plan de
  restore.

## Rollback

Este ticket solo añade documentación. No hay cambios de datos ni migraciones
que revertir.
