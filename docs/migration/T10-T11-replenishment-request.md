# T10/T11 — ReplenishmentRequest manual y supply

Estado: seam v2 y lifecycle durable implementados y verificados.

La ruta `POST /api/v2/buyer-companies/{companyId}/replenishment-requests`
reutiliza el servicio de solicitudes existente, pero obtiene el tenant del
path protegido por `TenantAccess`. Valida producto activo, proveedor dueño del
producto, cantidad positiva y fecha de entrega no vencida. El origen se fija a
`MANUAL` y no se acepta desde el cliente.

La ruta v1 se conserva como adapter de compatibilidad. El request lifecycle
persiste idempotency key, versión, cancelación y consumo único de aceptación;
la reserva atómica de supply se ejecuta antes de crear el FuelOrder.
