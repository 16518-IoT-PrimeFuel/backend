# T20 — Proyección de outbox a notificaciones

Estado: primera proyección implementada y verificada el 2026-09-24.

`DurableEventPublisher` persiste el outbox y publica el evento de aplicación;
`OutboxRelay` recupera eventos pendientes. `DurableEventNotificationHandler`
escucha después del commit y crea la notificación solo para eventos con
destinatario IAM resuelto. Actualmente cubre
`FuelRequestCreated`, `FuelRequestApproved` y `FuelRequestRejected`.

Si IAM no puede resolver un destinatario, el evento durable se conserva y no se
inventa un usuario. `source_event_key` hace el fanout idempotente durante
replay. La entrega externa (push/email) sigue siendo responsabilidad de un
consumidor posterior.
