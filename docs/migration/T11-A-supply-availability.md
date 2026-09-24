# T11-A — Validación de disponibilidad de supply

Estado: validación inicial implementada el 2026-09-24.

Las solicitudes manuales v2 solo aceptan productos activos y cantidades que no
superen el stock disponible declarado por el proveedor. La reserva atómica de
stock queda deliberadamente pendiente del siguiente ticket: requiere definir
la política de reserva, expiración y carrera accept/reject antes de descontar
inventario.
