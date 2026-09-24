# T05/T06 — Customer Assets y Tank

Estado: expansión estructural, backfill reversible y snapshot de lecturas
implementados y verificados.

Se añadieron `customer_accounts`, `customer_sites` y `tanks` con referencias
explícitas al tenant y al cliente. `tanks` conserva capacidad, unidad, nivel
actual, tipo de combustible, estado y última lectura para que la telemetría
posterior no dependa de `equipment`.

La migración V5 es expand-only y V14 ejecuta un backfill idempotente: conserva
las tablas legacy, crea un sitio por cuenta cuando no existe y enlaza cada
`equipment` con un `tank.legacy_equipment_id` único. La API de alta de sitios y
tanques mantiene autorización tenant; la eliminación de legacy sigue fuera de
alcance hasta reconciliar conteos contra MySQL.
