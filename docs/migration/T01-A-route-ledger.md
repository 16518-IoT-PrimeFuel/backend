# T01-A — Ledger de rutas y contratos v1

Estado: baseline caracterizado el 2026-09-23.

## Baseline

- Commit verificado: `cef9bf2` (`feat/integrate-mobile-backend`, merge de `develop`).
- Fuentes Java: 314.
- Pruebas existentes: 10; resultado: 10 verdes.
- Java usado: 26.0.2.1.
- API documentada: `/api/v1`.
- Inventario estático actual: 77 mappings REST detectados en controllers.
- El roadmap declara 77 operaciones; el conteo estático coincide. Falta
  reconciliarlo con mappings runtime y OpenAPI generado.

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

## Gaps bloqueantes antes de T02

1. Generar snapshot runtime de Spring MVC y reconciliarlo con el número 77.
2. Añadir contratos de smoke para ordering, payment y fulfillment; hoy las
   pruebas de aplicación cubren principalmente IAM.
3. Añadir fixtures sintéticos de dos tenants y distinguir respuestas
   `known-gap` de invariantes deseadas.

## Rollback

Este ticket solo añade harness/documentación de pruebas. El rollback elimina
el snapshot y los artefactos del ledger; no modifica datos ni contratos.
