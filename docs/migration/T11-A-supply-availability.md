# T11-A — Validación de disponibilidad de supply

Estado: validación inicial implementada el 2026-09-24.

Las solicitudes manuales v2 solo aceptan productos activos y cantidades que no
superen el stock disponible declarado por el proveedor. Al aceptar una
solicitud, `SupplyReservationStore` descuenta stock con un `UPDATE` condicional
(`available_stock >= quantity`) y registra una reserva única por `request_id`.
Un retry devuelve la reserva existente y no vuelve a descontar stock.

La expiración/liberación de reservas queda pendiente de definir junto con el
lifecycle de delivery.
