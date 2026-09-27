# T24-B — Retiro de endpoints v1 confirmado por producto

Decisión de producto del 2026-09-26: retirar de inmediato las familias de vehículos, conductores y calificaciones, sin esperar la ventana de métricas. El 2026-09-27 se completó también el retiro de solicitudes de combustible, una vez que la aceptación v2 pudo crear y vincular una orden en una sola transacción.

## Rutas retiradas

| Familia | Rutas retiradas | Continuidad |
|---|---|---|
| Vehículos (5) | `GET/POST /api/v1/vehicles`, `GET/PUT/DELETE /api/v1/vehicles/{id}` | `/api/v2/tankers` |
| Conductores (5) | `GET/POST /api/v1/drivers`, `GET/PUT/DELETE /api/v1/drivers/{id}` | `/api/v2/drivers` |
| Calificaciones (3) | `GET/POST /api/v1/provider-ratings`, `PUT /api/v1/provider-ratings/{id}` | Sin reemplazo. |
| Solicitudes de combustible (5) | `POST/GET /api/v1/fuel-requests`, `GET /api/v1/fuel-requests/{requestId}`, `POST /api/v1/fuel-requests/{requestId}/accept`, `POST /api/v1/fuel-requests/{requestId}/reject` | `/api/v2/replenishment-requests`; aceptación en `POST /api/v2/replenishment-requests/{requestId}/accept`. |

## Código, datos y transacción

El primer retiro eliminó los controladores v1 de vehículos/conductores y el slice sin consumidores internos de `ProviderRating`. `drivers` y `vehicles` permanecen porque Fleet v2 las usa; V32 elimina la tabla física `provider_ratings`.

La continuación v2 de solicitudes ahora recibe `deliveryAddress` y `deliveryDate`. La fecha es obligatoria y no puede ser anterior al día de negocio de America/Lima. Si falta la dirección se toma del sitio asociado a la cisterna. La aceptación, el consumo de la decisión, la creación de `FuelOrder` mediante la lógica ya existente en ordering y la vinculación de la orden se ejecutan en una sola transacción. La respuesta incluye `orderId`; si faltan los mapeos de cliente o cisterna al identificador legacy, responde 409 y revierte la aceptación.

Se eliminaron `FuelRequestsController`, `LegacyFuelRequestBridge`, `FuelRequestService`, la entidad, repositorio, recursos y estado del aggregate legacy, además del test dedicado al bridge. No quedan consumidores internos. V34 ejecuta `DROP TABLE fuel_requests`; la columna histórica `fuel_orders.request_id` se conserva y las órdenes creadas desde v2 la dejan nula. No había claves foráneas entrantes a `fuel_requests`.

V33 agrega `delivery_address` y `delivery_date` a `replenishment_requests`. No se modificaron migraciones anteriores ni `scripts/seed-first-admin.sql`.

## Pruebas y consumidores

Los golden y characterization tests de aceptación, aislamiento, autorización y entrega usan la API v2 como preparación. Se mantiene la cobertura de entrega, pagos y límites entre tenants; se elimina únicamente el test dedicado al bridge v1. `OpenApiSnapshotTest` actualiza el snapshot con 59 operaciones v1 activas y `ApiLedgerSelfCheckTest` compara el ledger actualizado.

En los consumidores externos inspeccionados, `frontend` no contiene llamadas a las rutas retiradas. En `Mobile-app/docs/specs/USER_STORIES.md` hay menciones a la antigua solicitud; su reemplazo es `POST /api/v2/replenishment-requests` y sus operaciones v2 de decisión.

## Riesgos y verificación

El despliegue requiere aplicar V33 antes de usar los nuevos campos y V34 después de confirmar que ningún dato histórico de `fuel_requests` deba conservarse. El cambio no valida MySQL desde este repositorio. La verificación local ejecuta la suite Maven con H2.
