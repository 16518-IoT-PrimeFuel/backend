# T22-B — Adapters y plan de sunset verificado (S22)

El sunset general conserva sus gates de medición. Por decisión de producto, T24-B retiró de inmediato Fleet v1, provider-ratings y fuel-requests; estas decisiones no esperaron la ventana de métricas.

## 1. Golden contracts v1/v2 (coexistencia verificada)

La coexistencia v1↔v2 ya está cubierta por tests verdes:

| Contrato | Test |
|---|---|
| v1 request→order→delivery→payment (golden path) | `OrderFulfillmentGoldenPathTest` |
| v1 delivery mapping + journal (T14-B) | `DeliveryV1LifecycleMappingTest` |
| v1 delivery legacy create (dual-mode, T15-B) | `DeliveryV1OrchestrationTest` |
| v2 delivery lifecycle | `DeliveriesV2ControllerTest` |
| v2 assignment orchestration | `AssignDeliveryFlowTest` |
| v1 POST notificaciones (deprecado) + v2 `/me` | `MeNotificationsControllerTest` |
| v1 inventory/Fleet T5 (Swagger) | build verde del ledger 59/59 (`ApiLedgerSelfCheckTest`) |

Se agregó `V1V2CoexistenceGoldenTest` para dejar explícito que la misma operación responde en ambas versiones
(v1 create de delivery + v2 lectura/assignación) sin romperse mutuamente.

## 2. Métricas por versión (implementadas en T24-PRE-METRICS)

Se cuenta uso por patrón con `ApiRouteMetricsInterceptor` y la persistencia V27 (`count`, `last_seen`,
`distinct_callers`). El reporte actual es una consulta bajo `GET /api/v2/admin/api-metrics`, con filtro de
versión; no corre un scheduler semanal.
- Un sunset sólo puede aprobarse con `last_seen` dentro de la ventana acordada y `distinct_callers = 0`, más el
  ledger externo (frontend/mobile).

> Estado: **instrumentación implementada**; aprobar sunset aún requiere una ventana real de medición y el
> ledger externo de consumidores.

## 3. Registro de sunset

Cada familia requiere: **consumer/ledger**, **versión destino**, **última observación**, **owner**, **fecha
aprobada**. Ninguna celda de fecha está aprobada todavía.

| Familia (# rutas) | Destino | Prerequisito para sunset | Owner | Ventana | Ejecutado |
|---|---|---|---|---|---|
| Drivers/Vehicles v1 (10) | fleet v2 | Decisión de producto T24-B (2026-09-26) | Fleet | inmediata | ✅ sí |
| Deliveries v1 (8) | delivery v2 (ya existe) | ledger externo + métricas | Fulfillment | propuesta | ❌ no |
| Notifications v1 (7) | `/me/notifications` v2 (ya existe) | ledger externo + métricas | Notifications | propuesta | ❌ no |
| Solicitudes v1 (5) | replenishment-requests v2 | T24-B; aceptación crea orden vinculada | Ordering | inmediata | ✅ sí |
| FuelProducts v1 (7) | supply v2 | ledger + delete→deactivate | Supply | propuesta | ❌ no |
| Equipment v1 (5) | tanks/customers v2 | ledger + mapeo metadata | Equipment | propuesta | ❌ no |
| IAM v1 (14) | organization/`/me` v2 | ledger + rol plataforma | IAM | propuesta | ❌ no |
| Payments v1 (7) | v2 financiero (spec futura) | U12/spec + ledger | Payment | propuesta | ❌ no |
| Analytics v1 (3) | insights v2 | rol plataforma + scope tenant | Reporting | propuesta | ❌ no |
| Provider-ratings (3) | sin sustituto | Decisión de producto T24-B (2026-09-26) | Product | inmediata | ✅ sí |
| DEPRECATE (favorite-provider, directorio global, orden directa/confirm, notifications POST) | sin sustituto | U13/ledger | Product | propuesta | ❌ no |

## 4. Blockers heredados de T22-A (impiden ejecutar)

1. **Ledger externo `UNKNOWN`**: sin repo cliente no se puede probar "no uso" → ningún sunset aprobable.
2. **`ROLE_ADMIN` inasignable**: las rutas administrativas (33, 40, 59, 71, 75, #7) son inalcanzables; hay que
   decidir el rol de plataforma antes de tocarlas (no verás tráfico real hasta entonces).
3. **DEPRECATE sin sustituto**: deprecadas pero no retirables hasta cerrar consumidores.

## 5. Comunicación de ventana (plantilla, no enviada)

> Se comunica que las rutas **v1** de la familia *X* quedarán deprecadas y se retirarán el *fecha* (≥90 días
> desde el aviso). Los consumidores deben migrar a *v2 destino*. El retiro no usa redirects para métodos de
> escritura y no revierte datos. Fuente de verdad: este registro + `T22-A-consumer-audit-and-v2-contracts.md`.

## 6. Estado del sunset

- Se retiraron en T24-B las rutas de Fleet v1 y provider-ratings por decisión de producto.
- Los retiros de otras familias siguen sujetos a las métricas y evidencia de consumidores externas.
- Fuel requests permanece activa hasta completar la aceptación con creación y vínculo transaccional de la orden.

## Build

La verificación de la suite completa queda registrada en `T24-B-endpoint-retirement.md`. V32 elimina la tabla `provider_ratings`; no se borra información de drivers/vehicles ni de fuel requests.

T24-B ejecutó el retiro inmediato de Fleet v1 y calificaciones por decisión de producto. Fuel requests no se retiró: su aceptación v2 aún no crea una orden con los datos de entrega necesarios. El resto de familias conserva sus gates.
