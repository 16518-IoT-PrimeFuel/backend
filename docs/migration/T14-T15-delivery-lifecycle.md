# T14/T15 — Lifecycle físico de delivery y asignación

Estado: primera separación implementada y verificada el 2026-09-24.

Crear un delivery deja el estado `SCHEDULED`; despacharlo lo lleva a
`DISPATCHED`, llegar a `ARRIVED` y completarlo a `DELIVERED`. Las transiciones
inválidas devuelven conflicto. La creación usa ports de Fulfillment para
reservar supply y fleet antes de asignar, reutiliza la reserva de supply por
`request_id` y usa el `order_id` como clave de compatibilidad para órdenes
legacy.

La reserva de conductor/vehículo está protegida por locks de fila, ventana,
capacidad, tenant e idempotency key. La liberación al completar/fallar ocurre
en la misma transacción del cierre.
