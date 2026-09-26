# T18-A — Comandos de válvula firmados

## Contrato

Al persistir una decisión `AUTHORIZED`, y únicamente con `safety.valve.commands.enabled=true`, el listener síncrono crea un comando `OPEN` ligado a `deliveryId`, `providerId` y `decisionId` en la misma transacción. `BLOCKED`, `NO_POLICY` y la bandera apagada no crean comandos. La firma usa HMAC-SHA256 de la stdlib sobre `v1|{commandId}|{deliveryId}|{action}|{nonce}|{expiresAt ISO-8601}`. Cada nonce es UUID y vence a los 120 segundos. Los uniques son por entrega para no filtrar existencia entre tenants; un tercer unique por entrega/decisión evita emitir dos comandos para una decisión.

Se publican `valve.operation.requested.v1` y `valve.command.sent.v1` por `EventPublicationRegistry`; esos eventos son la entrega lógica al gateway, sin adaptador de red ni firmware. Los comandos `PENDING` se revocan al recibir `delivery.failed.v1` con estado terminal `FAILED` o `CANCELLED`.

Activación local: definir `SAFETY_VALVE_SIGNING_SECRET` con un secreto aleatorio y arrancar con `--safety.valve.commands.enabled=true` (o `SAFETY_VALVE_COMMANDS_ENABLED=true`). No hay secreto predeterminado. Certificación física y firmware quedan fuera de alcance.

## Persistencia y validación

Migración `V28__valve_commands.sql`. Validación MySQL: pendiente, manual por el usuario. La expiración se concilia perezosamente al leer o conciliar el comando en T18-B; no hay scheduler.

## Verificación

`ValveCommandIssuerTest` verifica la firma recalculándola desde los campos persistidos, el apagado global y la revocación. T17-B prueba que únicamente una decisión autorizada publicada por la evaluación puede activar este listener.
