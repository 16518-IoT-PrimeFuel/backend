# T10-B — Cierre del puente de solicitudes legacy (S10)

## Estado

El puente `LegacyFuelRequestBridge` dejó de existir al completar T24-B. La solicitud de abastecimiento v2 es el único flujo de creación y decisión. La aceptación orquesta, dentro de una transacción, la transición a `ACCEPTED`, el consumo único de esa decisión, la creación de `FuelOrder` por la seam pública de ordering y la vinculación de su identificador a la solicitud.

La creación de la orden reutiliza `FuelOrderCommandService`; no copia el cálculo de precio ni las validaciones de producto/equipo. La orquestación entre bounded contexts vive en `applicationflows`. Para construir la orden se resuelven `customerAccountId → legacyCompanyId` y `tankId → equipmentId`; si falta cualquiera de los mapeos, la API devuelve 409 y se revierte la transacción.

Las solicitudes manuales exigen fecha de entrega no anterior al día de negocio de Lima. La dirección se completa desde el sitio de la cisterna cuando se omite. Las solicitudes automáticas registran la dirección del sitio y la fecha actual de Lima.

## Retiro

Se eliminaron `FuelRequestsController`, `LegacyFuelRequestBridge`, `FuelRequestService` y su modelo, recursos y persistencia legacy. V34 elimina la tabla física `fuel_requests`; se conserva `fuel_orders.request_id` por compatibilidad histórica, pero las órdenes nuevas dejan ese campo nulo. No hay claves foráneas entrantes que deban quitarse.

El contrato vigente y los escenarios cubiertos quedan en `ReplenishmentAcceptanceIntegrationTest`, `ReplenishmentLimaBusinessDateTest`, `OrderFulfillmentGoldenPathTest` y `StateLifecycleRetryCharacterizationTest`.
