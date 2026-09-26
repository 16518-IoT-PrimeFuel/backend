# T17-B — Integración de decisión safety al lifecycle de delivery

## Contrato

U21 fija el único disparador en la entrada a `DELIVERING`. Cuando `complete` parte de `ARRIVED`, fulfillment
persiste `ARRIVED → DELIVERING` y publica `delivery.discharge.started.v1` mediante `EventPublicationRegistry`.
El listener síncrono de safety evalúa la última evidencia usando `GeofenceEvaluation`; después el flujo ejecuta
`completePhysical`. La transición y la decisión/outbox comparten la transacción.

Fulfillment no importa `safety.api`: safety ya depende de `fulfillment.api`, así que una llamada directa crearía
un ciclo. `JpaEventPublicationRegistry` persiste y emite el `EventEnvelope` de forma síncrona dentro de la
transacción; el listener normal `@EventListener` se une a ella. No se usa `@TransactionalEventListener`.

`AUTHORIZED`, `BLOCKED` y `NO_POLICY` quedan registrados y nunca bloquean el avance. Un fallo de persistencia
o publicación escapa del listener y revierte la transición completa. Un `complete` que ya parte de
`DELIVERING` no vuelve a evaluar.

## Verificación

`DeliveryLifecycleTest` cubre evidencia fresca autorizada, posición stale, ausencia de política, proveedor
persistido desde el delivery, reintento desde `DELIVERING` y rollback completo ante fallo simulado al guardar
la decisión. `ModuleBoundaryRulesTest` pasa sin editar su store.

No se añade ruta ni migración; T17-A conserva las reglas U09 y no hay endpoint público de decisión ni override.
