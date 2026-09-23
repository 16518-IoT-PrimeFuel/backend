# T20-A — Destinatarios y suscriptores (S20)

Deps: T04-B, T10-B, T14-B, T19-B. **U17 resuelta: el canal inicial es solo in-app** (`NotificationChannel.IN_APP`);
push/marketing quedan explícitamente fuera.

> **Objetivo:** generar la bandeja desde eventos para los miembros autorizados del scope, con fanout
> idempotente y persistencia de intentos. No se toca la API v1 (eso es T20-B).

## Fuente de eventos (decisión)

No existía forma de que un `@EventListener` recibiera los hechos de `replenishment`/`delivery`: se escribían
**solo** al outbox (`event_publications`) y no había dispatcher (T19-B lo dejó fuera de alcance). Decisión
aprobada: **`JpaEventPublicationRegistry.publish` además emite el `EventEnvelope` como evento Spring
in-process** (un solo cambio en `shared`, cero cambios en los productores de delivery). Los listeners corren
sincrónicamente dentro de la transacción del productor, así que son defensivos.

Además, `ReplenishmentCommandServiceImpl` ahora **publica** `replenishment.accepted.v1` /
`replenishment.rejected.v1` con scope = `request.organizationId` (la organización cliente a informar, no un
valor del cliente), para que "accept/reject llega al scope correcto" sea real.

## Destinatarios

Se agregó el seam **`iam.api.MembershipDirectory.activeMemberUserIds(organizationId)`** (no existía:
`MembershipAccess` sólo resuelve el principal actual), implementado en
`iam/infrastructure/services/MembershipDirectoryImpl` sobre `MembershipRepository.findActiveByOrganizationId`.
Devuelve **solo miembros activos**, que es lo que hace que un revocado deje de recibir fanout nuevo.

## Persistencia (V20__notification_fanout.sql)

`notifications` gana: `organization_id`, `event_id`, `channel`, `delivery_status`, `attempts`,
`last_attempt_at`, y el único **`uk_notifications_event_recipient_channel (event_id, user_id, channel)`**.
`event_id` es nullable para las notificaciones manuales (v1 POST), y MySQL/PG permiten múltiples NULL en un
unique. Validado en MySQL 8.0.46 con `scripts/validate-schema-mysql.ps1`.

## Idempotencia (doble)

1. **`EventInbox.consume("notification-fanout", eventId)`** — un evento se consume una sola vez para este
   consumidor (replay-safe).
2. **unique (`event_id`, `user_id`, `channel`)** más una verificación previa — imposible duplicar la fila de un
   mismo destinatario.

## Invariantes

- **Unique event+recipient+channel:** sí (unique + inbox).
- **Revocado no recibe nuevo fanout:** sí (`activeMemberUserIds` excluye revocados).
- **Lectura no cambia negocio:** sí; `markAsRead` sólo toca `notifications`, y delivery no depende de
  notificación.
- **Destinatarios desde `iam.api`:** sí.

## Mapeo evento → notificación

`NotificationFanoutListener` mapea: `replenishment.accepted.v1 → ORDER_ACCEPTED`,
`replenishment.rejected.v1 → ORDER_REJECTED`, `delivery.completed.v1 → DELIVERY_COMPLETED`,
`delivery.failed.v1 → DELIVERY_FAILED`. Otros eventos se ignoran.

## Limitaciones / asunciones documentadas

- Los eventos de **delivery** publican `organizationId = providerId` (espacio de IDs legacy; ver T20-A/inventario).
  Ese id no es una organización con memberships, así que hoy no resuelve destinatarios → no genera fanout.
  Mapear provider→organización requiere el directorio legacy y es trabajo posterior; se documenta, no se
  inventa.
- "Persistir intentos": cada intento de fanout exitoso se persiste como su fila (con `attempts=1` y
  `delivery_status=DELIVERED`). Un **fallo** de un destinatario se loguea y se saltea para no romper la
  transacción del productor; persistir reintentos/DLQ formales es T19-B/T20-B.

## Tests

`NotificationFanoutTest` (4, H2): accept fans out a **todos** los miembros activos (y verifica
organizationId/eventId/channel/status/attempts/referenceId); reject fans out como `ORDER_REJECTED`; **replay**
del mismo envelope **no duplica**; **revocado** deja de recibir fanout nuevo (el owner sí lo recibe).

## Build

`./mvnw.cmd test` (JAVA_HOME `C:/Users/crama/.jdks/openjdk-26.0.2`): **191/191 verde** (4 skipped = IT MySQL
gated). Migración `V20` validada en MySQL 8.0.46.
