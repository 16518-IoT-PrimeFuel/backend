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

El estado inicial es `PENDING` y `attempts=0`. Un relay interno programado
recupera los `PENDING`, publica el envelope y los marca `PUBLISHED`; al no
existir todavía un broker externo, la entrega fuera de este proceso queda
explícitamente fuera de alcance.

## Gate cubierto

- migración H2 reproducible;
- restricción única de clave;
- publicación repetida sin duplicación;
- suite de aplicación y arquitectura verdes.

## Límite explícito

El relay actual es de una sola instancia y no sustituye un broker. Si se
despliega horizontalmente, el siguiente paso es un claim con lock/lease y DLQ.
