# T14/T15 — Lifecycle físico de delivery y asignación

Estado: primera separación implementada y verificada el 2026-09-24.

Crear un delivery deja el estado `SCHEDULED`; despacharlo lo lleva a
`DISPATCHED` y completarlo a `DELIVERED`. Las transiciones inválidas devuelven
conflicto. La creación reutiliza la reserva de supply por `request_id` y usa el
`order_id` como clave de compatibilidad para órdenes legacy, evitando descontar
stock dos veces.

La reserva de conductor/vehículo continúa protegida por disponibilidad y
pertenencia al proveedor. La liberación al completar sigue ocurriendo en la
misma transacción del cierre.
