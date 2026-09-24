# T03-B — Baseline sintético y datos de referencia

Estado: implementado y verificado hasta Flyway V15.

## Qué se creó

- `V1__synthetic_legacy_baseline.sql`: DDL de las 17 tablas declaradas por el
  modelo JPA actual.
- `V2__synthetic_reference_data.sql`: fixture exclusivo de tests con roles,
  una empresa compradora, un proveedor y un producto Diesel B5.
- `V3__durable_outbox.sql`: tabla de eventos durables con `event_key` único e
  idempotencia de publicación.
- `V4`–`V15`: memberships, customer assets/tanks, reservations, delivery
  evidence, valve ACK, device/telemetry, refill lifecycle y backfills
  idempotentes desde legacy.
- `SyntheticBaselineMigrationTest`: ejecuta ambas migraciones sobre H2 en modo
  MySQL y comprueba el número de tablas y registros.

Los mocks no están dentro de `src/main/resources`; por eso no se insertan en
desarrollo ni producción accidentalmente.

## Límites conocidos

Este baseline es estructural, no una copia certificada del legado. Se
mantuvieron las columnas y restricciones declaradas por JPA, pero no se
inventaron claves foráneas porque el modelo actual guarda esos IDs sin
relaciones JPA. La comparación con MySQL real sigue siendo el gate de T03-C.

## Verificación

```text
Suite completa: verde; el test de migración valida Flyway V15, 35 tablas,
cuentas/sitios backfilled y dos organizaciones.
```

El test específico confirma el baseline legacy, los artefactos nuevos y los
datos mock. La suite de aplicación confirma que publicar dos veces la misma
clave deja un solo evento en `outbox_events`.
