# T5 — Fecha de negocio en Lima

`ReplenishmentCommandServiceImpl.handle(CreateReplenishmentRequestCommand)` valida `deliveryDate` contra el día de
negocio en `America/Lima`, usando el `Clock` inyectado de `ClockConfiguration`. La validación se comparte entre
solicitudes manuales y automáticas; el controller solo deriva la organización autenticada y delega la creación.

`solicitud de abastecimientoLimaBusinessDateTest` fija el reloj en `2026-09-26T01:00:00Z` (25/09 20:00 en Lima), verifica que
el 25/09 se acepta y que una petición con fecha 24/09 responde HTTP 400 sin persistirse.
