# T5 — Fecha de negocio en Lima

`FuelRequestService.create` valida `deliveryDate` contra el día actual en `America/Lima`, usando el `Clock`
inyectado de `ClockConfiguration`. No se encontró otra validación `LocalDate.now()` en el código de
producción, así que el cambio queda en un solo punto.

`FuelRequestLimaBusinessDateTest` fija el reloj en `2026-09-26T01:00:00Z` (25/09 20:00 en Lima), verifica que
el 25/09 se acepta y que una petición con fecha 24/09 responde HTTP 400 sin persistirse.
