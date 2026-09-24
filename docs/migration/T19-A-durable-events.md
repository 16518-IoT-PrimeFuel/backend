# T19-A — Entrega durable e idempotente de eventos

Estado: primera implementación completada el 2026-09-24.

La aplicación ahora expone un contrato pequeño (`DurableEventPublisher`) y
persiste eventos en `outbox_events` dentro de la misma transacción de la
operación que los origina. La clave de negocio (`event_key`) es única: un
reintento devuelve `false` y no crea otro registro.

El flujo de solicitudes manuales publica actualmente:

- `FuelRequestCreated`
- `FuelRequestApproved`
- `FuelRequestRejected`

El estado inicial es `PENDING` y `attempts=0`. El worker/releaser que entregue
los eventos a un broker todavía es una etapa posterior; mientras no exista,
los eventos quedan recuperables en la base de datos.

## Gate cubierto

- migración H2 reproducible;
- restricción única de clave;
- publicación repetida sin duplicación;
- suite de aplicación y arquitectura verdes.

## Pendiente

Añadir consumidor/releaser con backoff, métricas, claim concurrente y prueba de
replay contra el broker cuando se decida la tecnología de transporte.
