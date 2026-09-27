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

La creación v2 valida en el servicio que `customerAccountId` pertenezca a la organización activa y que `tankId`, cuando se envía, pertenezca a ese cliente. Un cliente o cisterna inexistente o de otro tenant responde 404 sin exponer datos; la dirección predeterminada del sitio se resuelve después de validar la pertenencia. `deliveryDate` no puede ser anterior al día de negocio de America/Lima y el servicio aplica la misma regla a los flujos automáticos. La aceptación, el consumo de la decisión, la creación de `FuelOrder` mediante la lógica existente de ordering y la vinculación de la orden se ejecutan en una sola transacción. La respuesta incluye `orderId`; si falta un mapeo heredado de cliente o cisterna, responde 409 y revierte la aceptación.

Se eliminaron `FuelRequestsController`, `LegacyFuelRequestBridge`, `FuelRequestService`, la entidad, repositorio, recursos y estado del aggregate legacy, además del test dedicado al bridge. No quedan consumidores internos. V34 primero copia dirección y fecha de `fuel_requests` a las solicitudes v2 enlazadas por `episode_key = CONCAT('fuel-request:', id)` cuando falte alguno de esos datos; después elimina la tabla física `fuel_requests`. Las filas v1 sin solicitud v2 enlazada se pierden porque ya estaban fuera del flujo v2. La columna histórica `fuel_orders.request_id` se conserva y las órdenes creadas desde v2 la dejan nula. No había claves foráneas entrantes a `fuel_requests`.

V35 agrega `uk_deliveries_order_id` sobre `deliveries.order_id`. Los duplicados preexistentes impiden aplicar la migración; la consulta de detección queda comentada junto al ALTER y no se eliminan datos automáticamente. Si dos comandos distintos compiten por la misma orden, uno crea la entrega y el otro recibe 409.

V33 agrega `delivery_address` y `delivery_date` a `replenishment_requests`. No se modificaron migraciones anteriores ni `scripts/seed-first-admin.sql`.

## Pruebas y consumidores

Los golden y characterization tests de aceptación, aislamiento, autorización y entrega usan la API v2 como preparación. Se mantiene la cobertura de entrega, pagos y límites entre tenants; se elimina únicamente el test dedicado al bridge v1. `OpenApiSnapshotTest` actualiza el snapshot con 59 operaciones v1 activas y `ApiLedgerSelfCheckTest` compara el ledger actualizado.

En los consumidores externos inspeccionados, `frontend` no contiene llamadas a las rutas retiradas. En `Mobile-app/docs/specs/USER_STORIES.md` hay menciones a la antigua solicitud; su reemplazo es `POST /api/v2/replenishment-requests` y sus operaciones v2 de decisión.

## Riesgos y verificación

El despliegue aplica V33 antes de V34 y V35. V34 rescata los datos de entrega de las solicitudes v1 enlazadas; las filas v1 no enlazadas se eliminan con la tabla. Una solicitud v2 histórica que conserve dirección o fecha nula responde 409 al aceptarse, sin cambiar estado ni crear orden, y debe recibir esos datos antes del intento. V35 requiere que `deliveries.order_id` no tenga duplicados; la migración no borra registros. El cambio no valida MySQL desde este repositorio. La verificación local ejecuta la suite Maven con H2.
