# T4 — Auditoría ponytail

## Interfaces de una sola implementación

Eliminadas las interfaces `CustomerBackfillService`, `TankBackfillService`, `TankReadingService`,
`DeviceCredentialService`, `DeliveryLifecycleService`, `SupplyReservationService` y `TelemetryIngestService`.
Cada una tenía una única implementación de producción y los tests no declaraban implementaciones ni fakes.
Los consumidores ahora inyectan la clase concreta. `ProvisionedCredential` e `IngestResult` se declararon en
sus implementaciones para mantener el dato de retorno sin una interfaz portadora.

Se conservaron todas las interfaces de servicios `*CommandService`/`*QueryService`, repositorios de dominio,
APIs públicas y servicios compartidos indicados en el alcance.

## Dependencias y recursos sin uso

- `commons-lang3` se eliminó. El resultado de sign-in usa `UserCommandService.SignInResult`; no queda
  referencia `org.apache.commons.lang3` en `src/`.
- `MessageResource` se eliminó tras confirmar cero referencias en `src/main` y `src/test`.
- Se eliminó el directorio vacío `src/test/java/com/primefuel/fulltank/platform/persistence/`.
- Se conservaron `telemetry/` y `equipment/devicebinding/` por la decisión W6.

`ModuleBoundaryRulesTest` pasó sin modificar su baseline.
