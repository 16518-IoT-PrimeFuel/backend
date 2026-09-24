# T03-B — Baseline sintético y datos de referencia

Estado: implementado y verificado el 2026-09-24.

## Qué se creó

- `V1__synthetic_legacy_baseline.sql`: DDL de las 17 tablas declaradas por el
  modelo JPA actual.
- `V2__synthetic_reference_data.sql`: fixture exclusivo de tests con roles,
  una empresa compradora, un proveedor y un producto Diesel B5.
- `V3__durable_outbox.sql`: tabla de eventos durables con `event_key` único e
  idempotencia de publicación.
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
Tests run: 16, Failures: 0, Errors: 0, Skipped: 0
```

El test específico confirma 17 tablas y 2 roles de referencia, además de un
producto mock. La suite de aplicación confirma que publicar dos veces la misma
clave deja un solo evento en `outbox_events`.
