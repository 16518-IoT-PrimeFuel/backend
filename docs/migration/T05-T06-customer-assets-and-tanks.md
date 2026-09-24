# T05/T06 — Customer Assets y Tank

Estado: expansión estructural implementada y verificada el 2026-09-24.

Se añadieron `customer_accounts`, `customer_sites` y `tanks` con referencias
explícitas al tenant y al cliente. `tanks` conserva capacidad, unidad, nivel
actual, tipo de combustible, estado y última lectura para que la telemetría
posterior no dependa de `equipment`.

La migración V5 es expand-only: no mueve ni elimina todavía los datos de
`buyer_companies`/`equipment`. El siguiente gate es un backfill idempotente y
la API de alta/consulta de sitios y tanques con autorización tenant.
