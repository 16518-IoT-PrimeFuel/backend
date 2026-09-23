# T20-B — Bandeja compatible y reintentos (S20)

Depende de T20-A. Añade la bandeja `/me`, mantiene la lectura v1 compatible y **deprecia** (no rompe) el POST
v1.

## Rutas nuevas (v2)

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/api/v2/me/notifications` | Bandeja del usuario autenticado |
| GET | `/api/v2/me/notifications/unread` | Bandeja no leída del usuario autenticado |
| POST | `/api/v2/me/notifications/{notificationId}/read` | Marca como leída una notificación propia (idempotente) |

`MeNotificationsController` resuelve el usuario con **`iam.api.MembershipAccess.currentUserId()`** — no hay id
de usuario en la ruta, así que un caller solo puede leer/marcar **su propia** bandeja (privacidad). 403 si no
hay principal; 404 si la notificación no existe o es de otro usuario.

## Compatibilidad v1

- `GET /api/v1/notifications/**` **sin cambios** (siguen verdes y compatibles).
- `POST /api/v1/notifications` **deprecado, no roto**: se añadió `@Deprecated` y
  `@Operation(deprecated = true)`; el comportamiento es idéntico (mismos códigos). El frontend ya no debería
  fabricar notificaciones (la bandeja se genera por eventos desde T20-A). Su retiro real queda supeditado al
  ledger de consumidores (S22/T24-B), no a este ticket.

## Idempotencia / replay

Marcar como leída es idempotente (repetir deja `read=true`, no duplica filas). El fanout de T20-A ya es
replay-safe por `EventInbox` + unique `(event_id, user_id, channel)`; aquí se verifica además a nivel de
bandeja.

## Tests

`MeNotificationsControllerTest` (2, MockMvc, H2):
- **Privacidad**: dos miembros de la misma organización reciben el fanout de accept; cada uno ve solo la suya
  (`$.length()==1` y `userId` propio); un usuario **no** puede marcar como leída la de otro (404).
- **Idempotencia**: marcar leída dos veces → 200 y `read=true`, sigue habiendo una sola notificación; la
  bandeja no leída queda vacía.
- **Compatibilidad**: el POST v1 deprecado sigue devolviendo 201 con el cuerpo original.

## Build

`./mvnw.cmd test`: **193/193 verde** (4 skipped = IT MySQL gated). Sin cambios de esquema.

Nota: la deprecación agrega `deprecated: true` a la operación v1 en el snapshot OpenAPI que
`OpenApiSnapshotTest` regenera; no afecta el conteo de 77 rutas v1 (`ApiLedgerSelfCheckTest` sigue verde).
