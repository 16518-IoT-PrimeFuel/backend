# T18 — Ledger de comando y ACK de válvula

Estado: ledger de ACK implementado el 2026-09-24; hardware deliberadamente
deshabilitado.

Un comando requiere una geocerca activa y un punto dentro de ella. Se persiste
como `PENDING` con `command_id` único. El ACK mueve el registro a `ACKED` y es
idempotente. En este corte no existe ningún driver MQTT/HTTP ni escritura a un
actuador físico: el ledger solo deja evidencia verificable para el siguiente
integrador de firmware.
