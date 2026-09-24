# T01-A — Ledger de rutas y contratos v1

Estado: baseline caracterizado el 2026-09-23.

## Baseline

- Commit verificado: `6eed6d6` (`feat/integrate-mobile-backend`).
- Fuentes Java: 431.
- Suite actual: resultado verde con Flyway V15 y prueba de contexto Spring.
- Java usado: 26.0.2.1.
- API documentada: `/api/v1`.
- Inventario runtime actual: 77 mappings `/api/v1`, protegido por una prueba
  de conteo; los endpoints `/api/v2` son aditivos.
- La reconciliación OpenAPI completa sigue siendo una tarea separada porque el
  contrato generado no está versionado en el repositorio.

## Inventario por bounded context actual

| Contexto | Controller(s) | Mappings actuales |
|---|---|---:|
| IAM | authentication, users, buyer-companies, provider-companies | 14 |
| Equipment | equipment | 6 |
| Inventory | fuel-products | 7 |
| Ordering | fuel-requests, fuel-orders | 12 |
| Payment | payments | 7 |
| Fulfillment | deliveries, drivers, vehicles | 18 |
| Catalog | provider-ratings | 3 |
| Notification | notifications | 7 |
| Reporting | analytics | 3 |
| **Total estático** | 16 controllers | **77** |

> Nota: el conteo por familia incluye rutas declaradas en el código. La fuente
> final de verdad para T01-A será el snapshot generado desde
> `RequestMappingHandlerMapping` con la aplicación levantada.

## Contratos que deben congelarse

Para cada mapping se conservarán método, ruta, parámetros, body, status,
schema de respuesta, regla de autenticación, regla de ownership y estado de
negocio observado. No se corrigen reglas en T01; los defectos se etiquetan
como `known-gap`.

| Familia | Rutas principales | Estado T01 |
|---|---|---|
| IAM | `/authentication/*`, `/users`, `/buyer-companies`, `/provider-companies` | `characterized` por pruebas existentes |
| Equipment | `/equipment` | `partial` |
| Inventory | `/fuel-products` | `partial` |
| Ordering | `/fuel-requests`, `/fuel-orders` | `partial` |
| Payment | `/payments` | `partial` |
| Fulfillment | `/deliveries`, `/drivers`, `/vehicles` | `partial` |
| Catalog | `/provider-ratings` | `partial` |
| Notification | `/notifications` | `partial` |
| Reporting | `/analytics` | `partial` |

## Gaps que permanecen fuera del baseline sintético

1. Versionar un snapshot de schemas OpenAPI generado en CI.
2. Añadir contratos de smoke para ordering, payment y fulfillment; hoy las
   pruebas de aplicación cubren principalmente IAM.
3. Añadir fixtures sintéticos persistentes de dos tenants y distinguir respuestas
   `known-gap` de invariantes deseadas.

## Rollback

Este ticket solo añade harness/documentación de pruebas. El rollback elimina
el snapshot y los artefactos del ledger; no modifica datos ni contratos.
