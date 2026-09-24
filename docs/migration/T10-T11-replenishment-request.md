# T10/T11 — ReplenishmentRequest manual y supply

Estado: seam v2 implementada y verificada el 2026-09-24.

La ruta `POST /api/v2/buyer-companies/{companyId}/replenishment-requests`
reutiliza el servicio de solicitudes existente, pero obtiene el tenant del
path protegido por `TenantAccess`. Valida producto activo, proveedor dueño del
producto, cantidad positiva y fecha de entrega no vencida. El origen se fija a
`MANUAL` y no se acepta desde el cliente.

La ruta v1 se conserva como adapter de compatibilidad. El siguiente gate es
resolver snapshots de tanque/producto y reserva de supply antes de aceptar una
solicitud bajo concurrencia.
