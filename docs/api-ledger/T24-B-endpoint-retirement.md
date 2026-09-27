# T24-B — Retiro de endpoints v1 confirmado por producto

Decisión de producto del 2026-09-26: retirar de inmediato las familias de vehículos, conductores y calificaciones, sin esperar la ventana de métricas. El retiro de solicitudes de combustible queda bloqueado hasta que la aceptación v2 cree y vincule una orden dentro de una misma transacción.

## Rutas retiradas

| Familia | Rutas retiradas | Continuidad |
|---|---|---|
| Vehículos (5) | `GET/POST /api/v1/vehicles`, `GET/PUT/DELETE /api/v1/vehicles/{id}` | `GET/POST /api/v2/tankers`, `GET/PUT /api/v2/tankers/{tankerId}`, activar/desactivar en v2. |
| Conductores (5) | `GET/POST /api/v1/drivers`, `GET/PUT/DELETE /api/v1/drivers/{id}` | `/api/v2/drivers` con tenant desde principal, elegibilidad y ciclo activar/desactivar. |
| Calificaciones (3) | `GET/POST /api/v1/provider-ratings`, `PUT /api/v1/provider-ratings/{id}` | Sin reemplazo. |

## Código y datos

Se retiraron los controladores v1 y recursos de vehículos/conductores, y el slice completo sin consumidores de `ProviderRating` (controlador, recurso, agregado, repositorios y adaptador JPA). `drivers` y `vehicles` permanecen: son las tablas que usa Fleet v2. V32 elimina únicamente la tabla física `provider_ratings`, que no tiene claves foráneas entrantes.

La migración `V32__drop_retired_tables.sql` ejecuta `DROP TABLE provider_ratings`. No se modifica ninguna migración aplicada ni `scripts/seed-first-admin.sql`.

## Pruebas y consumidores

Los flujos `OrderFulfillmentGoldenPathTest`, `DeliveryV1LifecycleMappingTest`, `V1V2CoexistenceGoldenTest`, `StateLifecycleRetryCharacterizationTest`, `CrossTenantIsolationTest` y `PaymentCharacterizationTest` preparan flota mediante endpoints v2. Los contratos de los endpoints v1 retirados ya no existen; los contratos de entrega, seguridad entre tenants y pagos siguen caracterizados.

`frontend` está vacío. En `Mobile-app/docs/specs/USER_STORIES.md:62` y `:131` quedan menciones futuras a `/api/v1/fuel-requests`; para migrar ese consumidor: `POST /api/v2/replenishment-requests` y `POST /api/v2/replenishment-requests/{id}/reject`. Los repos consumidores no se modificaron.

## Bloqueo pendiente de solicitudes

`CreateReplenishmentRequestResource` no recibe dirección ni fecha de entrega. El contrato y aggregate v2 tampoco guardan esos datos. El flujo legacy exigía dirección y validaba `deliveryDate` respecto de `LocalDate.now(clock.withZone(ZoneId.of("America/Lima")))`. Aceptar v2 creando una orden sin esos datos no preservaría las validaciones PORT-2/T5. Además, mientras no haya orden vinculada, `POST /api/v2/deliveries` no puede resolver la aceptación para esa orden.

Por eso permanecen `FuelRequestsController`, `LegacyFuelRequestBridge`, el aggregate `FuelRequest` y la tabla física `fuel_requests`; no se agrega V32 para esa tabla. Antes de retirar esa familia hay que definir y persistir sus datos de entrega en v2, y mover la creación de orden al composition root con ambas escrituras en una transacción.

## Verificación

`OpenApiSnapshotTest` regenera el snapshot con 64 operaciones v1. `ApiLedgerSelfCheckTest` se ajusta al ledger después de retirar estas 13 rutas. `./mvnw.cmd test` finalizó con 270 pruebas, 0 fallos, 0 errores y 4 omitidas.
