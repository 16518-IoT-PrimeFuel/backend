# T17/T21 — Geocerca versionada y journal de trazabilidad

Estado: evaluación y persistencia inicial implementadas el 2026-09-24.

Cada configuración de geocerca crea una nueva versión y supersede la activa.
La evaluación usa distancia geodésica y devuelve `inside`, versión y distancia
calculada. Los puntos de tracking también se escriben en `delivery_journal`
con el mismo `eventId` idempotente.

La geocerca solo detecta; no bloquea ni activa ningún actuador. El journal es
append-only para este flujo. El siguiente gate es probar replay/offline y
definir retención, privacidad y el contrato de ACK antes de considerar safety
preventivo.
