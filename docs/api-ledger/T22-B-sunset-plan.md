# T22-B — Adapters y plan de sunset verificado (S22)

Depende de T22-A. **Prepara el terreno: NO retira ningún endpoint.** Este documento es el registro de sunset y
el plan de medición; la ejecución (retiro real) es T24-B y está **bloqueada** hasta que este registro esté
aprobado.

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
| v1 inventory/Fleet T5 (Swagger) | build verde del ledger 77/77 (`ApiLedgerSelfCheckTest`) |

Se agregó `V1V2CoexistenceGoldenTest` para dejar explícito que la misma operación responde en ambas versiones
(v1 create de delivery + v2 lectura/assignación) sin romperse mutuamente.

## 2. Métricas por versión (plan)

Para aprobar un sunset hace falta medir el uso real de cada versión. Hoy **no existe** telemetría de rutas por
versión; el plan es:

- Instrumentar el filtro/auth para etiquetar cada request con `path.version` (`v1`/`v2`) y el
  `controller#method`, y agregar contadores por ruta (Micrometer/actuator o el registro interno de T19-A).
- Reporte semanal por ruta: `count`, `last_seen`, `distinct_callers` (tenant/company), para poblar
  `última observación` del registro.
- Un sunset sólo puede aprobarse con `last_seen` dentro de la ventana acordada y `distinct_callers = 0`, más el
  ledger externo (frontend/mobile).

> Estado: **plan documentado, no implementado.** Es prerequisito de T24-B, no de este ticket.

## 3. Registro de sunset (NO ejecutado)

Cada familia requiere: **consumer/ledger**, **versión destino**, **última observación**, **owner**, **fecha
aprobada**. Ninguna celda de fecha está aprobada todavía.

| Familia (# rutas) | Destino | Prerequisito para sunset | Owner | Ventana | Ejecutado |
|---|---|---|---|---|---|
| Drivers/Vehicles v1 (10) | fleet v2 (ya existe) | ledger externo + métricas | Fleet | propuesta | ❌ no |
| Deliveries v1 (8) | delivery v2 (ya existe) | ledger externo + métricas | Fulfillment | propuesta | ❌ no |
| Notifications v1 (7) | `/me/notifications` v2 (ya existe) | ledger externo + métricas | Notifications | propuesta | ❌ no |
| FuelRequests/Orders v1 (12) | replenishment-requests v2 | ledger + mapper IDs + cierre S10 | Ordering | propuesta | ❌ no |
| FuelProducts v1 (7) | supply v2 | ledger + delete→deactivate | Supply | propuesta | ❌ no |
| Equipment v1 (5) | tanks/customers v2 | ledger + mapeo metadata | Equipment | propuesta | ❌ no |
| IAM v1 (14) | organization/`/me` v2 | ledger + rol plataforma | IAM | propuesta | ❌ no |
| Payments v1 (7) | v2 financiero (spec futura) | U12/spec + ledger | Payment | propuesta | ❌ no |
| Analytics v1 (3) | insights v2 | rol plataforma + scope tenant | Reporting | propuesta | ❌ no |
| DEPRECATE (provider-ratings, favorite-provider, directorio global, orden directa/confirm, notifications POST) | sin sustituto | U13/ledger | Product | propuesta | ❌ no |

## 4. Blockers heredados de T22-A (impiden ejecutar)

1. **Ledger externo `UNKNOWN`**: sin repo cliente no se puede probar "no uso" → ningún sunset aprobable.
2. **`ROLE_ADMIN` inasignable**: las rutas administrativas (33, 40, 59, 71, 75, #7) son inalcanzables; hay que
   decidir el rol de plataforma antes de tocarlas (no verás tráfico real hasta entonces).
3. **DEPRECATE sin sustituto**: deprecadas pero no retirables hasta cerrar consumidores.

## 5. Comunicación de ventana (plantilla, no enviada)

> Se comunica que las rutas **v1** de la familia *X* quedarán deprecadas y se retirarán el *fecha* (≥90 días
> desde el aviso). Los consumidores deben migrar a *v2 destino*. El retiro no usa redirects para métodos de
> escritura y no revierte datos. Fuente de verdad: este registro + `T22-A-consumer-audit-and-v2-contracts.md`.

## 6. Garantías de este ticket

- **No se retiró ningún endpoint** ni se cambió ningún contrato (solo se agregó un test de coexistencia y este
  documento).
- El retiro (T24-B) queda **bloqueado** hasta aprobación explícita del registro (ledger + métricas + rol
  plataforma).

## Build

`./mvnw.cmd test`: verde (ver checklist). Sin cambios de contrato ni de esquema.
