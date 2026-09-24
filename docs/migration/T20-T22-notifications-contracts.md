# T20/T22 — Notificaciones y coexistencia v1/v2

Estado: adapter v2 de lectura implementado el 2026-09-24.

Se añadieron:

- `GET /api/v2/users/{userId}/notifications`
- `GET /api/v2/users/{userId}/notifications/unread`

Ambas rutas reutilizan el query service existente y exigen ownership del
usuario. Las rutas v1 permanecen activas; todavía no se retira ninguna ruta ni
se cambia el contrato de escritura.

La proyección event-driven desde outbox queda pendiente de definir con el
consumidor de eventos y la política de reintentos.
