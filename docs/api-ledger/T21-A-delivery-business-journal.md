# T21-A — Journal transaccional de negocio

## Fuentes inmutables

El journal se compone de `delivery_state_transitions` (estados), `safety_decisions` (decisiones geofence) y `delivery_business_journals` (comando lógico enviado, observaciones de válvula e incidentes). Las dos primeras ya son append-only en dominio/persistencia. La tercera captura esos eventos síncronamente desde `EventPublicationRegistry` dentro de la misma transacción; su repositorio solo ofrece `save` y lectura. Una falla al registrar un hecho aborta esa transacción para evitar que se confirme el cambio sin su journal.

`valve_commands` conserva el estado operativo mutable del ACK/expiración y no se usa como journal. Los hitos GPS `LOAD` se leen desde `transport_evidence_samples` para la timeline, pero se borran con la retención U18; no forman parte del journal durable, como exige esa decisión. Las entradas de tracking borradas nunca eliminan transiciones, decisiones ni `delivery_business_journals`.

Migraciones `V30__delivery_business_journal.sql` y `V37__align_delivery_business_journal_table_name.sql` (la estrategia de nombres pluraliza la tabla, igual que V25). Validación MySQL: pendiente, manual por el usuario.

## Verificación

`DeliveryTimelineControllerTest` prueba que transiciones y decisión/comando aparecen en las fuentes de journal; la eliminación GPS conserva el resto. La interfaz de `DeliveryBusinessJournalRepository` no expone `update` ni `delete`.
